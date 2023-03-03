package com.microsoft.plaiground.gameserver.entities.actions;

import com.microsoft.plaiground.common.utils.MinecraftLogger;
import com.microsoft.plaiground.gameserver.utils.GameTrackingHelper;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;


public class PlayerMoveAction extends Action {

  private final Location targetLocation;
  protected static final long stateCheckIntervalTicks = 20;
  protected long defaultTimeoutMs = 20000;

  @Override
  public String toString() {
    return "Action[PlayerMove to:" + targetLocation.toString() + " every:"
        + ((Long) stateCheckIntervalTicks).toString() + "ticks]";
  }

  /**
   * Sets new target location for Agent. Movement is not instantaneous, the animation may take some
   * seconds to render. Moving to locations far from current position may be expensive to compute.
   * Does not perform any action if Agent has not been spawned
   *
   * @param agentKey Unique identifier for instance of Agent
   * @param targetLocation new location with x, y, z coordinates where NPC will be moved.
   */
  public PlayerMoveAction(UUID agentKey, Location targetLocation) {
    super(agentKey);
    this.targetLocation = targetLocation;
  }

  @Override
  protected long getStateCheckIntervalTicks() {
    return stateCheckIntervalTicks;
  }

  @Override
  public Boolean hasTimedOut() {
    return System.currentTimeMillis() > getStartTimeMs() + defaultTimeoutMs;
  }

  @Override
  public void setUp() {
    if (!npc.isSpawned()) {
      MinecraftLogger.info("NPC " + agentKey + " cannot simulate" + this.toString()
          + " because it has not been spawned");
      this.transitionToState(ActionState.FAILURE);
      return;
    }

    if (targetLocation.getY() < 0) {
      MinecraftLogger.warning("Application of " + this.toString() + " for agent " + agentKey
          + " failed: The target location's Y is below 0: " + targetLocation.toString());
      this.transitionToState(ActionState.FAILURE);
      return;
    }

    // action validation: if a specific movement region is set, only allow NPC to move within that region
    var optGeometryInfo = GameTrackingHelper.getMovementRegionForPlayer(agentKey);
    if (optGeometryInfo.isPresent() && !optGeometryInfo.get()
        .isPointInXZColumnDefinedByGeometry(this.targetLocation.toVector())) {
      MinecraftLogger.warning("Application of " + this.toString() + " for agent " + agentKey
          + " failed: The target location " + targetLocation.toString()
          + " is outside of the movement region");
      this.transitionToState(ActionState.FAILURE);
      return;
    }
  }

  @Override
  public void execute() {
    npc.teleport(targetLocation, TeleportCause.PLUGIN);

    transitionToState(ActionState.SUCCESS);
  }

  public Location getTargetLocation() {
    return targetLocation;
  }
}
