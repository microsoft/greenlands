package com.microsoft.plaiground.gameserver.entities.actions;

import com.microsoft.plaiground.common.utils.BlockUtils;
import com.microsoft.plaiground.common.utils.MinecraftLogger;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;

public class BlockBreakAction extends Action {

  private final Location targetLocation;
  protected long defaultTimeoutMs = 2000;

  /**
   * Action to play Block damage and hand stroke animations.
   *
   * @param agentKey Unique identifier for instance of Agent
   * @param targetLocation Location of block to destroy
   */
  public BlockBreakAction(UUID agentKey, Location targetLocation) {
    super(agentKey);
    this.targetLocation = targetLocation;
  }

  @Override
  public String toString() {
    return "Action[BreakBlock at:" + targetLocation.toString() + "]";
  }

  @Override
  public void setUp() {
    if (!npc.isSpawned()) {
      MinecraftLogger.info("NPC " + agentKey + " cannot simulate" + this.toString()
          + " because it has not been spawned");
      transitionToState(ActionState.FAILURE);
      return;
    }

    // bot is not allowed to break blocks at 0 index (the floor)
    if (targetLocation.getBlockY() == 0) {
      MinecraftLogger.warning(
          "Application of " + this.toString() + " for agent " + agentKey
              + " failed: Cannot break in the floor (y == 0)");
      transitionToState(ActionState.FAILURE);
      return;
    }

    // block to be broken is not air block
    if (targetLocation.getBlock().isEmpty() || targetLocation.getBlock().isLiquid()) {
      MinecraftLogger.warning(
          "Application of " + this.toString() + " for agent " + agentKey
              + " failed: The target location is an empty/fluid block. Cannot break air/fluid.");
      transitionToState(ActionState.FAILURE);
      return;
    }

    // TODO: Add code to animate arm during block break to look more natural

    BlockUtils.setMaterialOfBlock(targetLocation, Material.AIR);
    transitionToState(ActionState.SUCCESS);
  }

  @Override
  protected long getStateCheckIntervalTicks() {
    return 1;
  }

  @Override
  public Boolean hasTimedOut() {
    return System.currentTimeMillis() > getStartTimeMs() + defaultTimeoutMs;
  }

  @Override
  public void execute() {
  }

  public Location getTargetLocation() {
    return targetLocation;
  }
}
