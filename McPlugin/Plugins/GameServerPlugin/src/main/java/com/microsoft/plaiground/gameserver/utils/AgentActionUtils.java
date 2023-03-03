package com.microsoft.plaiground.gameserver.utils;

import com.microsoft.plaiground.gameserver.constants.GameServerConstants;
import java.util.UUID;
import javax.annotation.Nullable;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;


public class AgentActionUtils {

  public static class CheckIfBlockLocationIsAccessibleResult {

    public final boolean isAccessible;
    public final @Nullable String errorMessage;

    CheckIfBlockLocationIsAccessibleResult(boolean isAccessible, @Nullable String errorMessage) {
      this.isAccessible = isAccessible;
      this.errorMessage = errorMessage;
    }
  }

  /**
   * Perform checks to see whether the agent can place/remove a block found at the given location.
   *
   * In case that the agent is not able to place/remove the block, this method will also log a
   * warning as to why that is the case.
   *
   * @param agentKey Unique identifier for instance of Agent
   * @param npc NPC instance of the agent
   * @param isBlockPlaceAction Whether the action is a block place action or a block remove action
   * @param targetBlockLocation Location of the block to place/remove
   * @return A {@link CheckIfBlockLocationIsAccessibleResult} that contains the result of the check,
   * and, in case of failure, the reason for the failure.
   */
  public static CheckIfBlockLocationIsAccessibleResult checkIfBlockLocationIsAccessible(
      UUID agentKey,
      NPC npc,
      boolean isBlockPlaceAction,
      Location targetBlockLocation
  ) {
    // if a specific movement region is set, only allow NPC to act within that region
    var optGeometryInfo = GameTrackingHelper
        .getMovementRegionForPlayer(agentKey);

    if (optGeometryInfo.isPresent()
        && !optGeometryInfo.get().isPointInXZColumnDefinedByGeometry(targetBlockLocation.toVector())
    ) {
      return new CheckIfBlockLocationIsAccessibleResult(
          false,
          "Target location "
              + targetBlockLocation.toString() + " is outside of the movement region"
      );
    }

    // check that target location is not too far away
    var livingEntity = (LivingEntity) npc.getEntity();
    var eyeLocation = npc.getStoredLocation().clone();
    eyeLocation.setY(eyeLocation.getY() + livingEntity.getEyeHeight());

    var distanceAgentToTargetLocation = eyeLocation.distance(targetBlockLocation);
    if (distanceAgentToTargetLocation > GameServerConstants.MAX_INTERACTION_DISTANCE) {
      return new CheckIfBlockLocationIsAccessibleResult(
          false,
          "Distance from Agent eye location to target location is "
              + String.format("%.2f", distanceAgentToTargetLocation)
              + " which is beyond the maximum allowed distance of "
              + GameServerConstants.MAX_INTERACTION_DISTANCE + "."
              + "\nAgent eye location: " + eyeLocation
              + "\nTarget location " + targetBlockLocation.toString()
      );
    }

    // check that NPC has an unobstructed view of the target location
    // TODO here we're ignoring the target location position since we can infer it from
    //  ^ The current orientation of the NPC. Is this correct? Maybe we should be using the direction
    //  ^ TO the target location instead of the eyeLocation.direction ?
    var rayTraceResult = npc.getEntity().getWorld().rayTraceBlocks(
        eyeLocation,
        eyeLocation.getDirection(),
        GameServerConstants.MAX_INTERACTION_DISTANCE,
        FluidCollisionMode.NEVER,
        true
    );

    if (rayTraceResult == null) {
      return new CheckIfBlockLocationIsAccessibleResult(
          false,
          "Raytrace operations didn't return a result"
              + "\nAgent eye location: " + eyeLocation.toString()
              + "\nRay direction " + eyeLocation.getDirection()
      );
    }

    if (rayTraceResult.getHitBlock() == null || rayTraceResult.getHitBlockFace() == null) {
      return new CheckIfBlockLocationIsAccessibleResult(
          false,
          "Raytrace didn't hit any blocks within the max distance of "
              + GameServerConstants.MAX_INTERACTION_DISTANCE
              + "\nAgent eye location: " + eyeLocation.toString()
              + "\nRay direction " + eyeLocation.getDirection()
      );
    }

    // if we're removing a block then we care for the "solid" block that the raytrace operation
    // just hit
    var relevantBlock = rayTraceResult.getHitBlock();

    if (isBlockPlaceAction) {
      // if this is a block place action then we care for the block that is adjacent to the
      // one hit by the raytrace operation. The transparent block that is immediately before it.
      relevantBlock = rayTraceResult
          .getHitBlock()
          .getRelative(rayTraceResult.getHitBlockFace());
    }

    return new CheckIfBlockLocationIsAccessibleResult(true, null);
  }
}
