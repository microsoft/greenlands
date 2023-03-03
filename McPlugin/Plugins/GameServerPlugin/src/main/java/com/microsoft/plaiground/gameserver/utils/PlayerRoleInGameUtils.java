package com.microsoft.plaiground.gameserver.utils;

import com.microsoft.plaiground.client.model.PlayerState;
import com.microsoft.plaiground.common.data.records.GameConfig;
import com.microsoft.plaiground.common.data.records.PlayerGameConfig;
import com.microsoft.plaiground.common.entities.GeometryInfo;
import com.microsoft.plaiground.common.utils.BlockUtils;
import com.microsoft.plaiground.common.utils.LocationUtils;
import com.microsoft.plaiground.common.utils.MinecraftLogger;
import com.microsoft.plaiground.common.utils.WorldUtils;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerRoleInGameUtils {

  public static void setPlayerRoleConfiguration(
      Player player,
      PlayerGameConfig playerGameConfig,
      @Nullable PlayerState initialPlayerState
  ) {

    // convert from PlaiGround GameMode enum to bukkit's and apply
    switch (playerGameConfig.gameMode) {
      case SURVIVAL -> player.setGameMode(GameMode.SURVIVAL);
      case CREATIVE -> player.setGameMode(GameMode.CREATIVE);
      case SPECTATOR -> player.setGameMode(GameMode.SPECTATOR);
      case ADVENTURE -> player.setGameMode(GameMode.ADVENTURE);
    }

    player.setAllowFlight(playerGameConfig.canToggleFlight);
    player.setFlying(playerGameConfig.canToggleFlight);

    player.setInvisible(!playerGameConfig.canBeSeenByOtherPlayers);

    if (initialPlayerState != null) {
      var initialInventory = initialPlayerState.getInventory();
      if (initialInventory != null) {
        for (var inventoryEntry : initialInventory.entrySet()) {
          var material = BlockUtils.MATERIAL_NAMES[Integer.parseInt(inventoryEntry.getKey())];
          var quantity = inventoryEntry.getValue();

          player.getInventory().addItem(new ItemStack(material, quantity));
        }
      }
    }
  }

  /**
   * Creates the movement region for role if there is one. If there is then it also returns the
   * {@link GeometryInfo}  for the region.
   */
  public static Optional<GeometryInfo> setMovementAreaForRole(
      UUID playerId,
      GameConfig gameConfig,
      @Nullable PlayerState initialPlayerState
  ) {
    if (initialPlayerState == null || initialPlayerState.getMovementRegion() == null) {
      MinecraftLogger.info("Movement region for player " + playerId + " is not set. Skipping...");
      return Optional.empty();
    }

    var movementRegionCube = initialPlayerState.getMovementRegion();
    var movementRegionGeometryInfo = GeometryInfo.fromAreaCube(movementRegionCube);
    MinecraftLogger.info("Setting movement region for player " + playerId);
    MinecraftLogger.info("Min corner: " + movementRegionGeometryInfo.getMinCorner()
        + " to Max Corner: " + movementRegionGeometryInfo.getMaxCorner());

    // persist movement region info for player, so we can reference it later on
    GameTrackingHelper.setMovementRegionForPlayer(playerId, movementRegionGeometryInfo);

    // set visual representation of where player is allowed to move
    // TODO this only works because we know world is flat!
    var gameWorldName = GameWorldUtils.getGameWorldName(gameConfig.gameId);
    GameWorldUtils.drawRegionBorderBlocks(gameWorldName, movementRegionGeometryInfo,
        Material.TARGET);

    return Optional.of(movementRegionGeometryInfo);
  }


  /**
   * If there is a custom spawn point set for the role then we return that. If there is a movement
   * region set for the agent, AND the specified spawn point is outside, then return the center of
   * the region. Otherwise, return the world's default spawn point.
   */
  public static Location computeSpawnLocationForRole(
      String worldName,
      @Nullable PlayerState playerState,
      @Nullable GeometryInfo movementRegion
  ) {
    var world = WorldUtils.getWorldWithName(worldName).getCBWorld();
    var spawnLocation = playerState != null
        ? playerState.getSpawnLocation()
        : null;

    if (spawnLocation == null && movementRegion == null) {
      // if neither spawn location nor movement region is set, then use default spawn point
      MinecraftLogger.info("Neither spawn location nor movement region are set, "
          + "spawning player at world spawn location");
      return world.getSpawnLocation();

    } else if (spawnLocation != null && movementRegion == null) {
      // if only the spawn location is set, then spawn at the spawn location
      MinecraftLogger.info("Spawn location is set and movement region is NOT, "
          + "spawning player at specified location.");
      return LocationUtils.convertToBukkitLocation(
          world,
          spawnLocation);

    } else if (spawnLocation == null && movementRegion != null) {
      // if only the movement region is set, then spawn at the region's center
      MinecraftLogger.info("Spawn location is NOT set but movement region IS set, "
          + "spawning player in center of movement region");
      return movementRegion.center.toLocation(world);

    } else {
      // if we get here then we know that both movement region and spawn location are set
      var isSpecifiedSpawnInsideMovementRegion = movementRegion
          .isPointInXZColumnDefinedByGeometry(
              LocationUtils.convertToVector(spawnLocation)
          );

      if (isSpecifiedSpawnInsideMovementRegion) {
        // if spawn location is inside the movement region, then use it
        MinecraftLogger.info("Spawn location is set inside the movement region, "
            + "spawning player at specified location.");
        return LocationUtils.convertToBukkitLocation(
            world,
            spawnLocation);

      } else {
        // otherwise, the specified location is outside the movement region then
        // adjust the spawn location to be the center of the region
        MinecraftLogger.warning(
            "Specified spawn location " + spawnLocation + " is outside of movement region (center: "
                + movementRegion.center + ", size: " + movementRegion.size + ") spawn "
                + "location for player will return center of movement region. ");
        return movementRegion.center.toLocation(world);
      }
    }
  }
}
