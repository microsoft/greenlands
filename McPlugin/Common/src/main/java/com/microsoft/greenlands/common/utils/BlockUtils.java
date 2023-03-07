package com.microsoft.greenlands.common.utils;

import com.microsoft.greenlands.client.model.Block;
import com.microsoft.greenlands.client.model.Location;
import com.microsoft.greenlands.common.constants.CommonConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;

public class BlockUtils {

  public static final Material[] MATERIAL_NAMES = Material.values();
  public static final HashMap<Material, Integer> MATERIAL_IDS =
      (HashMap<Material, Integer>) IntStream.range(0, MATERIAL_NAMES.length)
          .boxed()
          .collect(Collectors.toMap(i -> MATERIAL_NAMES[i], i -> i, Integer::sum));

  /**
   * Updates Material of block in world, at given location.
   *
   * @param world world where block will be placed
   * @param location block location
   * @param materialId index of desired material in this.MATERIAL_NAMES
   */
  public static void setMaterialOfBlock(World world, Location location, int materialId) {
    var bukkitLocation = new org.bukkit.Location(
        world,
        location.getX(),
        location.getY(),
        location.getZ());
    var targetMaterial = MATERIAL_NAMES[materialId];
    setMaterialOfBlock(world, bukkitLocation, targetMaterial);
  }

  /**
   * Updates Material of block in world, at given location.
   *
   * @param world world where block will be placed
   * @param x x location of block to replace
   * @param y y location of block to replace
   * @param z z location of block to replace
   * @param material index of desired material in this.MATERIAL_NAMES
   */
  public static void setMaterialOfBlock(World world,
      double x, double y, double z,
      Material material
  ) {
    var bukkitLocation = new org.bukkit.Location(
        world,
        x,
        y,
        z);
    setMaterialOfBlock(world, bukkitLocation, material);
  }

  /**
   * Updates Material of block in world, at given location.
   *
   * @param world world where block will be placed
   * @param location block location
   * @param material new block material
   */
  public static void setMaterialOfBlock(World world, org.bukkit.Location location,
      Material material) {
    var blockToSet = world.getBlockAt(
        Double.valueOf(location.getX()).intValue(),
        Double.valueOf(location.getY()).intValue(),
        Double.valueOf(location.getZ()).intValue());

    blockToSet.setType(material);
  }

  /**
   * Updates Material of block in world using absolute coordinates x, y, z.
   *
   * @param location Bukkit location to place the block
   * @param material new block material
   */
  public static void setMaterialOfBlock(org.bukkit.Location location, Material material) {
    location.getWorld().getBlockAt(location).setType(material);
  }

  public static void addBlocksToWorld(World bukkitWorld,
      Map<String, com.microsoft.greenlands.client.model.Block> greenlandsBlockMap) {
    for (var locationBlockEntry : greenlandsBlockMap.entrySet()) {
      var location = LocationUtils.fromStringToGreenlandsLocation(locationBlockEntry.getKey());
      addBlockToWorld(bukkitWorld, location, locationBlockEntry.getValue());
    }
  }

  /**
   * Updates block in world with material and location from BlockLocation.
   *
   * @param world world where block will be placed
   * @param block block location with 3D coordinates and material
   */
  public static void addBlockToWorld(World world, Location location, Block block) {
    setMaterialOfBlock(world,
        location,
        block.getType());
  }

  /**
   * Creates a straight line of blocks that goes from position1 to position 2 (inclusive). Since
   * this is a straight line in 3D space then both of the provided locations need to be the same
   * except in only 1 dimension.
   *
   * If positions differ in more than 1 dimension then this method will throw an
   * {@link AssertionError}
   */
  public static void addStraightRowOfBlocksToWorld(
      World world,
      Material material,
      Vector pos1,
      Vector pos2
  ) {
    // TODO: eventually we could implement Bresenham's line algorithm so we're not constrained to only
    //   straight lines. https://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm

    // when drawing a straight line in 3d, all dimensions need to be the same except 1
    assert (
        // only x dim differs
        pos1.getBlockX() != pos2.getBlockX()
            && pos1.getBlockY() == pos2.getBlockY()
            && pos1.getBlockZ() == pos2.getBlockZ()
    ) || (
        // only z dim differs
        pos1.getBlockZ() != pos2.getBlockZ()
            && pos1.getBlockY() == pos2.getBlockY()
            && pos1.getBlockX() == pos2.getBlockX()
    ) || (
        // only y dim differs
        pos1.getBlockY() != pos2.getBlockY()
            && pos1.getBlockX() == pos2.getBlockX()
            && pos1.getBlockZ() == pos2.getBlockZ()
    ) || (
        // all dims are equal
        pos1.getBlockY() == pos2.getBlockY()
            && pos1.getBlockX() == pos2.getBlockX()
            && pos1.getBlockZ() == pos2.getBlockZ()
    ) :
        "Tried to add a straight line between two points but more than 1 dimension differs. Position 1: "
            + pos1 + " Position 2: " + pos2;

    if (pos1.getBlockX() != pos2.getBlockX()) {
      var minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
      var maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
      for (var xVar = minX; xVar <= maxX; xVar++) {
        BlockUtils.setMaterialOfBlock(
            world,
            xVar,
            pos1.getBlockY(),
            pos1.getBlockZ(),
            material
        );
      }
    } else if (pos1.getBlockZ() != pos2.getBlockZ()) {
      var minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
      var maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
      for (var zVar = minZ; zVar <= maxZ; zVar++) {
        BlockUtils.setMaterialOfBlock(
            world,
            pos1.getBlockX(),
            pos1.getBlockY(),
            zVar,
            material
        );
      }
    } else if (pos1.getBlockY() != pos2.getBlockY()) {
      var minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
      var maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
      for (var yVar = minY; yVar <= maxY; yVar++) {
        BlockUtils.setMaterialOfBlock(
            world,
            pos1.getBlockX(),
            yVar,
            pos1.getBlockZ(),
            material
        );
      }
    } else {
      MinecraftLogger.warning("Tried to create a straight line between two positions but both "
          + "positions are the same!");
    }
  }

  public static org.bukkit.block.Block getBlockAtLocation(World world,
      org.bukkit.Location location) {
    return world.getBlockAt(location);
  }

  public static org.bukkit.block.Block getBlockAtLocation(World world, Location location) {
    return world.getBlockAt(
        location.getX().intValue(),
        location.getY().intValue(),
        location.getZ().intValue()
    );
  }

  public static Block convertBukkitBlockToGreenlandsBlock(org.bukkit.block.Block bukkitBlock) {
    var block = new Block();
    block.setType(bukkitBlock.getType().ordinal());

    return block;
  }

  public static HashMap<String, Block> convertChunkToBlockMap(
      org.bukkit.ChunkSnapshot chunkSnapshot) {
    var chunkBlocks = new HashMap<String, Block>();
    var maxChunkX = CommonConstants.WORLD_MIN_CHUNK_SIZE;
    var maxChunkZ = CommonConstants.WORLD_MIN_CHUNK_SIZE;
    // Chunk Height documentation
    // https://minecraft.fandom.com/wiki/Java_Edition_1.18_Experimental_Snapshot_1#General
    var minChunkY = -64;
    var maxChunkY = 320;

    MinecraftLogger.finest(
        "Convert chunk at position x: " + chunkSnapshot.getX() + ", z: " + chunkSnapshot.getZ()
            + " to a Greenlands BlockMap");
    for (var x = 0; x < maxChunkX; x++) {
      for (var z = 0; z < maxChunkZ; z++) {
        for (var y = minChunkY; y < maxChunkY; y++) {
          var bukkitBlockMaterial = chunkSnapshot.getBlockType(x, y, z);

          // If block at location is NOT air, add it to the chunk
          if (bukkitBlockMaterial != Material.AIR) {
            var absoluteX = (chunkSnapshot.getX() * CommonConstants.WORLD_MIN_CHUNK_SIZE) + x;
            var absoluteZ = (chunkSnapshot.getZ() * CommonConstants.WORLD_MIN_CHUNK_SIZE) + z;
            MinecraftLogger.finest(
                "Adding block at absolute position x: " + absoluteX + ", y: " + y + " z: "
                    + absoluteZ
                    + " to a Greenlands BlockMap");
            // TODO: We currently don't use block data, but it is accessible.
            // var bukkitBlockData = chunkSnapshot.getBlockData(x, y, z);
            var greenlandsLocation = new Location()
                .x((float) absoluteX)
                .y((float) y)
                .z((float) absoluteZ)
                .pitch(0f)
                .yaw(0f);

            var greenlandsBlock = new Block();
            greenlandsBlock.setType(bukkitBlockMaterial.ordinal());

            var locationString = LocationUtils.fromGreenlandsLocationToString(greenlandsLocation);

            chunkBlocks.put(locationString, greenlandsBlock);
          }
        }
      }
    }

    return chunkBlocks;
  }

  public static HashMap<String, Block> convertChunksToBlockMap(
      List<org.bukkit.ChunkSnapshot> chunks) {
    var worldBlocks = new HashMap<String, Block>();

    for (var chunk : chunks) {
      var chunkBlockMap = BlockUtils.convertChunkToBlockMap(chunk);
      worldBlocks.putAll(chunkBlockMap);
    }

    return worldBlocks;
  }
}
