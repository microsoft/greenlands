package com.microsoft.plaiground.gameserver.entities.actions;

import com.microsoft.plaiground.common.utils.BlockUtils;
import com.microsoft.plaiground.common.utils.MinecraftLogger;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;

public class BlockPlaceAction extends Action {

  private final Location targetLocation;
  private final Material material;
  protected long defaultTimeoutMs = 2000;

  @Override
  public String toString() {
    return "Action[BlockPlace at:" + targetLocation.toString() + "]";
  }

  /**
   * Adds a block to world and executes arm swing animation.
   *
   * @param agentKey Unique identifier for instance of Agent
   * @param targetLocation new location with x, y, z coordinates of new block.
   * @param material material of new block.
   */
  public BlockPlaceAction(UUID agentKey, Location targetLocation, Material material) {
    super(agentKey);
    this.targetLocation = targetLocation;
    this.material = material;
  }

  @Override
  public void setUp() {
    if (!npc.isSpawned()) {
      MinecraftLogger.info("NPC " + agentKey + " cannot simulate" + this.toString()
          + " because it has not been spawned");
      transitionToState(ActionState.FAILURE);
      return;
    }

    // block to be placed is not air block
    if (material == Material.AIR) {
      MinecraftLogger.warning(
          "Application of " + this.toString() + " for agent " + agentKey
              + " failed: Cannot place Air block");
      transitionToState(ActionState.FAILURE);
      return;
    }

    // TODO: Add code to animate arm during block placement to look more natural

    BlockUtils.setMaterialOfBlock(targetLocation, material);
    transitionToState(ActionState.SUCCESS);
  }

  @Override
  public void execute() {
  }

  @Override
  protected long getStateCheckIntervalTicks() {
    return 1;
  }

  @Override
  public Boolean hasTimedOut() {
    return System.currentTimeMillis() > getStartTimeMs() + defaultTimeoutMs;
  }

  public Location getTargetLocation() {
    return targetLocation;
  }

  public Material getMaterial() {
    return material;
  }
}
