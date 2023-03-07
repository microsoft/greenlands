package com.microsoft.greenlands.gameserver.utils;

import com.microsoft.greenlands.client.model.AreaCube;
import com.microsoft.greenlands.client.model.GameChanges;
import com.microsoft.greenlands.client.model.GameState;
import com.microsoft.greenlands.client.model.GreenlandsTask;
import com.microsoft.greenlands.client.model.PlayerState;
import com.microsoft.greenlands.common.data.records.GameConfig;
import com.microsoft.greenlands.common.entities.GeometryInfo;
import com.microsoft.greenlands.common.utils.BlockUtils;
import com.microsoft.greenlands.common.utils.LocationUtils;
import com.microsoft.greenlands.common.utils.MetadataUtils;
import com.microsoft.greenlands.common.utils.MinecraftLogger;
import com.microsoft.greenlands.common.utils.WorldUtils;
import com.microsoft.greenlands.gameserver.enums.GameBlockFunctions;
import com.microsoft.greenlands.gameserver.enums.GameMetadataKeys;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;

public class GameWorldUtils {

  // Hard-coded to be outside of movement region (radius 9) and W letter (5) + 1 spacer block between region and letter
  // We want to guarantee the generated region of target structure doesn't overlap with existing region or letter so we duplicate radius and add a spacer block;
  private static final int movementRegionRadius = 9;
  private static final int letterSize = 5;
  private static final int rightSpacerSize = 1;
  private static final int leftSpacerSize = 3;
  // Visual which maps computation of offset to block distances. (Pictures shows leftSpacer as 1, but it is 3)
  // https://dev.azure.com/deeplearningeng/b9fef4e9-2d2e-401c-8aaf-355c8f4c8ee3/_apis/git/repositories/5cf359cc-c3f9-4827-a12b-5bfd8fd43b2e/pullRequests/427/attachments/image.png
  private static final int targetStructureOffset =
      movementRegionRadius + leftSpacerSize + letterSize + rightSpacerSize + movementRegionRadius;
  private static final String gameWorldPrefix = "game-world-";

  public static String getGameWorldName(String gameId) {
    return gameWorldPrefix + gameId;
  }

  public static boolean isGameWorld(World world) {
    return world.getName().startsWith(gameWorldPrefix);
  }

  /**
   * Deletes the game world for the given gameId.
   */
  public static void deleteGameWorld(String gameId) {
    WorldUtils.deleteWorld(getGameWorldName(gameId));
  }

  /**
   * Create a task world for the given GameId if it doesn't exist already. Return a boolean variable
   * indicating if a new world was created.
   */
  public static void createGameWorldIfNecessary(GameConfig gameConfig,
      GameState initialGameState,
      PlayerState initialPlayerState,
      List<GameChanges> targetGameChanges,
      GreenlandsTask greenlandsTask) {
    var worldName = getGameWorldName(gameConfig.gameId);

    if (!WorldUtils.worldExists(worldName)) {
      var world = WorldUtils.createNormalWorld(
          worldName,
          gameConfig.generatorName,
          greenlandsTask.getWorldSizeX(),
          greenlandsTask.getWorldSizeZ());

      var bukkitWorld = world.getCBWorld();

      // Apply blocks from initial game state to world
      var initialWorldBlockChanges = initialGameState.getWorldState().getBlockChanges();
      if (initialWorldBlockChanges != null && initialWorldBlockChanges.size() > 0) {
        MinecraftLogger.info("Adding initial blocks to world: " + initialWorldBlockChanges.size());
        BlockUtils.addBlocksToWorld(bukkitWorld, initialWorldBlockChanges);
      } else {
        MinecraftLogger.info("Initial blocks were not set!");
      }

      // Get last target change as target state
      var targetState = targetGameChanges.size() > 0
          ? targetGameChanges.get(targetGameChanges.size() - 1)
          : null;

      // if there is a target state in the task information then add it to the world
      if (targetState != null
          && targetState.getWorldChanges() != null
          && targetState.getWorldChanges().getBlockChanges() != null) {

        var targetBlockChanges = targetState.getWorldChanges().getBlockChanges();
        var targetStructureInfoOption = GeometryInfo.fromTargetStructure(targetBlockChanges);

        targetStructureInfoOption.ifPresent(targetStructureInfo -> {
          // Shift blocks from target structure so they can be seen side by side with the blocks from initial structure
          if (initialGameState.getPlayerStates() != null) {
            var playerStatesWithMovement = initialGameState.getPlayerStates()
                .entrySet()
                .stream()
                .filter(entry -> {
                  var playerState = entry.getValue();
                  return playerState.getMovementRegion() != null;
                })
                .collect(Collectors.toList());

            // If there are more than 1 players with movement regions defined, show a warning.
            // The server does not have information to know which movement region is correct to display under the target structure.
            if (playerStatesWithMovement.size() > 1) {
              MinecraftLogger.warning(
                  "There are movement regions defined for multiple players! Only a single movement region can be defined");
            }

            // If any player has a movement region, draw the movement region under the target structure as well
            if (playerStatesWithMovement.size() > 0) {
              var roleId = playerStatesWithMovement.get(0).getKey();
              MinecraftLogger.info("Using movement region defined for role " + roleId);

              var firstPlayerStateWithMovement = playerStatesWithMovement.get(0).getValue();

              var movementRegionCube = firstPlayerStateWithMovement.getMovementRegion();
              // Create clone of movement region cube
              var offsetMovementRegionCube = new AreaCube();
              offsetMovementRegionCube.setOrigin(
                  LocationUtils.clone(movementRegionCube.getOrigin()));
              offsetMovementRegionCube.setSize(LocationUtils.clone(movementRegionCube.getSize()));

              var offsetMovementRegionOriginX =
                  offsetMovementRegionCube.getOrigin().getX() - targetStructureOffset;
              offsetMovementRegionCube.getOrigin().setX((float) offsetMovementRegionOriginX);
              var offsetMovementRegionGeometryInfo = GeometryInfo.fromAreaCube(
                  offsetMovementRegionCube);

              MinecraftLogger.info("Drawing duplicate movement region border for target structure");
              MinecraftLogger.info("Min corner: " + offsetMovementRegionGeometryInfo.getMinCorner()
                  + " to Max Corner: " + offsetMovementRegionGeometryInfo.getMaxCorner());
              drawRegionBorderBlocks(worldName, offsetMovementRegionGeometryInfo, Material.TARGET);

              var movementRegionPadding = 2;
              var blockPlaceRegionCube = new GeometryInfo(
                  offsetMovementRegionGeometryInfo.size.clone()
                      .subtract(
                          new Vector(movementRegionPadding * 2, 0, movementRegionPadding * 2)),
                  offsetMovementRegionGeometryInfo.center.clone()
              );

              MinecraftLogger.info(
                  "Drawing duplicate block placement region grid for target structure");
              MinecraftLogger.info("Min corner: " + blockPlaceRegionCube.getMinCorner()
                  + " to Max Corner: " + blockPlaceRegionCube.getMaxCorner());
              fillRegionWithBlocks(worldName, blockPlaceRegionCube, Material.DIAMOND_BLOCK);
            }
          }

          for (var locationBlockEntry : targetBlockChanges.entrySet()) {
            var location = LocationUtils.fromStringToGreenlandsLocation(
                locationBlockEntry.getKey());
            location.setX((float) (location.getX() - targetStructureOffset));

            BlockUtils.addBlockToWorld(bukkitWorld, location, locationBlockEntry.getValue());

            MetadataUtils.setEntityMetadata(
                BlockUtils.getBlockAtLocation(bukkitWorld, location),
                GameMetadataKeys.GAME_BLOCK_ROLE,
                GameBlockFunctions.INITIAL_STRUCTURE);
          }
        });
      }
    }
  }

  public static void drawRegionBorderBlocks(
      String worldName,
      GeometryInfo geometryInfo,
      Material material) {
    var minCorner = geometryInfo.getMinCorner();
    var maxCorner = geometryInfo.getMaxCorner();

    // make minCorner 1 smaller so that players can't walk on lines on the "min"
    // side "max" side is already excluded by the way block placement works so
    // it's not necessary to edit maxCorner
    minCorner.subtract(new Vector(1, 0, 1));

    var world = WorldUtils.getWorldWithName(worldName);

    var bukkitWorld = world.getCBWorld();
    var floorYLevel = world.getSpawnLocation().getY() - 1;

    // top border
    BlockUtils.addStraightRowOfBlocksToWorld(bukkitWorld, material,
        new Vector(minCorner.getX(), floorYLevel, maxCorner.getZ()),
        new Vector(maxCorner.getX(), floorYLevel, maxCorner.getZ()));

    // bottom border
    BlockUtils.addStraightRowOfBlocksToWorld(bukkitWorld, material,
        new Vector(minCorner.getX(), floorYLevel, minCorner.getZ()),
        new Vector(maxCorner.getX(), floorYLevel, minCorner.getZ()));

    // left border
    BlockUtils.addStraightRowOfBlocksToWorld(bukkitWorld, material,
        new Vector(minCorner.getX(), floorYLevel, minCorner.getZ()),
        new Vector(minCorner.getX(), floorYLevel, maxCorner.getZ()));

    // right border
    BlockUtils.addStraightRowOfBlocksToWorld(bukkitWorld, material,
        new Vector(maxCorner.getX(), floorYLevel, minCorner.getZ()),
        new Vector(maxCorner.getX(), floorYLevel, maxCorner.getZ()));
  }

  public static void fillRegionWithBlocks(
      String worldName,
      GeometryInfo geometryInfo,
      Material material) {
    var minCorner = geometryInfo.getMinCorner();
    var maxCorner = geometryInfo.getMaxCorner();

    // When filling a region as opposed to setting border, we do expect the ranges to be inclusive so we subtract 1 from max instead of from min
    maxCorner.subtract(new Vector(1, 0, 1));

    var world = WorldUtils.getWorldWithName(worldName);

    var bukkitWorld = world.getCBWorld();
    var floorYLevel = world.getSpawnLocation().getY() - 1;
    var y = floorYLevel;

    MinecraftLogger.info("Fill region with material: " + material);

    for (var x = minCorner.getX(); x <= maxCorner.getX(); x++) {
      for (var z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
        BlockUtils.setMaterialOfBlock(
            bukkitWorld,
            x,
            y,
            z,
            material);
      }
    }
  }
}
