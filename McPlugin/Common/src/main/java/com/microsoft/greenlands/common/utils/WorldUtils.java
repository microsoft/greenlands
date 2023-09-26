package com.microsoft.greenlands.common.utils;

import com.microsoft.greenlands.common.constants.CommonConstants;
import com.microsoft.greenlands.common.providers.TaskDataProvider;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Helper functions to handle common world operations.
 */
public class WorldUtils {

  public static String LOBBY_WORLD_NAME = "lobbyWorld";
  public static String TEST_WORLD_NAME = "testWorld";

  // TODO: this was established empirically. Is there an optimal value? Is there a way in
  // which is done automatically by the server? Seems a bit weird having to do it manually
  private static final int CHUNKS_TO_LOAD_AROUND_SPAWN_AT_WORLD_CREATE = 10;

  /**
   * Teleports a player to world's spawn location. World must previously exists.
   *
   * @param player The player to be teleported.
   * @param worldName The name of the world where player is teleported.
   */
  public static void teleportPlayerToWorldSpawn(Player player, String worldName) {
    var world = getWorldWithName(worldName);

    player.teleport(world.getSpawnLocation());
  }

  /**
   * Teleports the player to the specified location. This will both move the player to the world the
   * location is in, and to the specific location within that world.
   *
   * @param player The player to be teleported.
   * @param location The location where you want to teleport the player.
   */
  public static void teleportPlayerToLocation(Player player, String worldName,
      com.microsoft.greenlands.client.model.Location location) {

    var targetWorld = getWorldWithName(worldName);
    var bukkitLocation = LocationUtils.convertToBukkitLocation(
        targetWorld.getCBWorld(),
        location);

    player.teleport(bukkitLocation);
  }

  /**
   * Teleports the player to the specified location. This will both move the player to the world the
   * location is in, and to the specific location within that world.
   *
   * @param player The player to be teleported.
   * @param location The location where you want to teleport the player.
   */
  public static void teleportPlayerToLocation(Player player, String worldName,
      Vector location) {

    var targetWorld = getWorldWithName(worldName);
    var bukkitLocation = location.toLocation(targetWorld.getCBWorld());

    player.teleport(bukkitLocation);
  }

  /**
   * Returns an {@link java.util.Optional} containing the {@link MultiverseWorld} instance for the
   * world with the specified name.
   */
  public static @Nonnull MultiverseWorld getWorldWithName(String worldName) {
    var wm = WorldUtils.getWorldManager();
    var world = wm.getMVWorld(worldName);

    assert world != null :
        "Tried to get a world that doesn't exist! " + "Expected to find a world with the name: "
            + worldName;

    return world;
  }

  /**
   * Obtains a reference to the Multiverse-Core Plugin manager.
   *
   * @return the Multiverse-Core manager.
   */
  public static MultiverseCore getMvCore() {
    var core = (MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");
    assert core != null :
        "Tried to obtain a reference to Multiverse-Core plugin but we didn't find it. "
            + " Is it installed as a server plugin?";

    return core;
  }

  /**
   * Obtains a reference to the Multiverse-Core Plugin World manager.
   *
   * @return the Multiverse-Core World manager.
   */
  public static MVWorldManager getWorldManager() {
    return getMvCore().getMVWorldManager();
  }

  /**
   * Creates a new flat grass world using worldManager with basic properties and rules.
   *
   * @param worldName Name of new world. No other world with this name must exists.
   * @return the created Multiverse world.
   */
  public static MultiverseWorld generateDefaultWorld(String worldName) {

    /*
     * > Generates a spaceworld with 1 deep stone world
     * mvcreate cleanroom1 normal -t flat -g CleanroomGenerator:.1|stone
     *
     * > Generates a world with no floor at all
     * mvcreate cleanroom1 normal -t flat -g CleanroomGenerator:.
     *
     * > Spaceworld with 1-deep bedrock layer (which is indestructible)
     * mvcreate cleanroom1 normal -t flat -g CleanroomGenerator:.1|bedrock
     * */
    return createNormalWorld(
        worldName,
        "CleanroomGenerator:.1|minecraft:grass_block",
        CHUNKS_TO_LOAD_AROUND_SPAWN_AT_WORLD_CREATE,
        CHUNKS_TO_LOAD_AROUND_SPAWN_AT_WORLD_CREATE);
  }

  /**
   * Checks if world exists in Multiverse.
   *
   * @param worldName Name of world to check
   * @return True if world exists
   */
  public static Boolean worldExists(String worldName) {
    return getWorldManager().isMVWorld(worldName);
  }

  /**
   * Creates the Lobby world used for pairing players without any structure.
   */
  public static void createLobbyWorld() {
    if (!worldExists(LOBBY_WORLD_NAME)) {
      var world = generateDefaultWorld(LOBBY_WORLD_NAME);
      world.setGameMode(GameMode.ADVENTURE);
    } else {
      MinecraftLogger.info("World %s already exists".formatted(LOBBY_WORLD_NAME));
    }
    populateInitialLobbyStructures();
  }

  /**
   * Creates a normal environment, flat, world with common world options.
   */
  public static @Nonnull MultiverseWorld createNormalWorld(
      @Nonnull String worldName,
      @Nonnull String generatorName,
      @Nonnull int worldSizeX,
      @Nonnull int worldSizeZ
  ) {
    var wm = getWorldManager();

    wm.addWorld(
        worldName,
        Environment.NORMAL,
        null,
        WorldType.FLAT,
        false,
        generatorName
    );

    var world = wm.getMVWorld(worldName);
    world.setGameMode(GameMode.SURVIVAL);
    world.setHunger(false);
    world.setAllowAnimalSpawn(false);
    world.setAllowMonsterSpawn(false);
    world.setAutoHeal(true);
    world.setEnableWeather(false);
    world.setTime("11:37am");
    world.setAllowFlight(true);

    WorldUtils.setCommonGameRulesForWorld(world);

    // whether the chunks at spawn point should always be loaded even if no players are present
    world.setKeepSpawnInMemory(false);

    // load chunks around spawn point automatically
    world.setAutoLoad(true);

    // Set corrected world spawn location in Bukkit World
    var spawnLocation = world.getSpawnLocation();
    var cbWorld = world.getCBWorld();
    cbWorld.setSpawnLocation(
        new Location(cbWorld,
            spawnLocation.getX(),
            spawnLocation.getY(),
            spawnLocation.getZ()));
    return world;
  }

  public static int convertWorldBlockSizeToChunkRadius(int worldBlockSize) {
    var worldBlockChunkRemainder = worldBlockSize % CommonConstants.WORLD_MIN_CHUNK_SIZE;
    if (worldBlockChunkRemainder != 0) {
      MinecraftLogger.warning(
          "World block size is not a multiple of chunk size! World block size: " + worldBlockSize);
    }

    var chunksSize = Math.floor(worldBlockSize / (double) CommonConstants.WORLD_MIN_CHUNK_SIZE);
    var chunksRadius = chunksSize / 2;

    return (int) chunksRadius;
  }

  /**
   * Loads a range of chunks around a given location in the world.
   *
   * A Chunk is a column of size 16(x) * 16(z) * 384(y), 98,304 blocks total. They extend from Y=-64
   * to Y=320.
   *
   * https://minecraft.wiki/w/Chunk
   */
  public static void loadChunksAroundLocation(
      World world,
      Location location,
      int chunkRangeX,
      int chunkRangeZ
  ) {
    var chunks = getChunksAroundLocation(world, location, chunkRangeX, chunkRangeZ);

    for (var chunk : chunks) {
      chunk.load(true);
    }
  }

  /**
   * Manually get chunks around a location
   * <pre>
   * Chunks (X) around location (L) look like this
   *     --rangeX--|--rangeX--
   *     ____________________
   *     X X X X X X X X X X |
   *     X X X X X X X X X X | rangeZ
   *     X X X X X X X X X X |
   *     X X X X X L X X X X |---
   *     X X X X X X X X X X |
   *     X X X X X X X X X X | rangeZ
   *     X X X X X X X X X X |
   * </pre>
   */
  public static List<Chunk> getChunksAroundLocation(
      World world,
      Location location,
      int chunkRangeX,
      int chunkRangeZ
  ) {
    var chunks = new ArrayList<Chunk>();

    for (int offsetX = -chunkRangeX; offsetX <= chunkRangeX; offsetX++) {
      for (int offsetZ = -chunkRangeZ; offsetZ <= chunkRangeZ; offsetZ++) {
        var locationX = (int) location.getX() + offsetX;
        var locationZ = (int) location.getZ() + offsetZ;

        MinecraftLogger.finest("Loading chunk at position x: " + locationX + ", z: " + locationZ);
        var chunk = world
            .getChunkAt(locationX, locationZ);

        chunks.add(chunk);
      }
    }

    return chunks;
  }

  /**
   * Saves block data
   */
  public static void saveWorldBlocksInChunkRadius(
      String taskId,
      World world,
      int chunkRadiusX,
      int chunkRadiusZ) {
    var chunks = getChunksAroundSpawnPoint(world, chunkRadiusX, chunkRadiusZ);
    var chunkSnapshots = convertChunksToSnapshots(chunks);

    AsyncHelper.run(() -> {
      var worldBlocks = BlockUtils.convertChunksToBlockMap(chunkSnapshots);
      TaskDataProvider.saveInitialWorldCompleteBlocks(taskId, worldBlocks);
      return null;
    });
  }

  public static List<ChunkSnapshot> convertChunksToSnapshots(List<Chunk> chunks) {
    var snapshots = new ArrayList<ChunkSnapshot>();
    for (var chunk : chunks) {
      snapshots.add(chunk.getChunkSnapshot(true, false, false));
    }
    return snapshots;
  }

  /**
   * Loads {@link #CHUNKS_TO_LOAD_AROUND_SPAWN_AT_WORLD_CREATE} chunks around the spawn of
   * {@code world}.
   */
  public static void loadChunksAroundSpawnPoint(
      @Nonnull MultiverseWorld world,
      @Nonnull int chunksRadiusX,
      @Nonnull int chunksRadiusZ
  ) {
    var bukkitWorld = world.getCBWorld();
    var spawnLocation = world.getSpawnLocation();

    MinecraftLogger.info(
        "Loading chunks around spawn point for world: " + world.getName() + " using radius "
            + chunksRadiusX + " x and " + chunksRadiusZ + " z");
    loadChunksAroundLocation(bukkitWorld, spawnLocation, chunksRadiusX, chunksRadiusZ);
  }

  public static List<Chunk> getChunksAroundSpawnPoint(
      @Nonnull World world,
      @Nonnull int chunksRadiusX,
      @Nonnull int chunksRadiusZ
  ) {
    var spawnLocation = world.getSpawnLocation();

    return getChunksAroundLocation(world, spawnLocation, chunksRadiusX, chunksRadiusZ);
  }

  /**
   * Deletes the world with the given name. Returns true if the world was deleted, false otherwise.
   */
  public static boolean deleteWorld(@Nonnull String worldName) {
    var wm = WorldUtils.getWorldManager();
    return wm.deleteWorld(worldName);
  }

  /**
   * Creates Test world used for debugging during development. If world exists, is deleted and
   * re-created.
   */
  public static void createTestWorld() {
    MinecraftLogger.info("Recreating %s world".formatted(TEST_WORLD_NAME));
    getWorldManager().deleteWorld(TEST_WORLD_NAME);
    var multiverseWorld = generateDefaultWorld(TEST_WORLD_NAME);
    multiverseWorld.setGameMode(GameMode.ADVENTURE);

    var world = Bukkit.getServer().getWorld(TEST_WORLD_NAME);

    var spawnLocation = world.getSpawnLocation();

    // Get a reference to block close to the spawn location
    var baseBlockLoc = spawnLocation.clone().add(0, 0, 2);
    world.getBlockAt(baseBlockLoc).setType(Material.GRASS_BLOCK, false);

    createSign(world,
        baseBlockLoc.clone().add(0, 1, 0),
        new String[]{"The world for", "your game is", "loading.", "Please wait."});
  }

  /**
   * Adds signs and other structures to Lobby world.
   */
  private static void populateInitialLobbyStructures() {
    // populate worlds with dummy structures
    var lobbyWorld = Bukkit.getServer().getWorld(LOBBY_WORLD_NAME);
    var spawnLocation = lobbyWorld.getSpawnLocation();

    var baseBlockLoc = spawnLocation.clone().add(0, 0, 2);
    lobbyWorld.getBlockAt(baseBlockLoc).setType(Material.GRASS_BLOCK, false);

    // TODO we could generate an empty spaceworld and use this same logic to generate small
    // "room-like" environments in the world

    createSign(lobbyWorld,
        baseBlockLoc.clone().add(0, 1, 0),
        new String[]{"", "Paste your join", "code to begin!"});
  }

  /**
   * Creates a Sign associated to a given task in a particular world.
   *
   * @param world World to create the sign.
   * @param location Location to create sign.
   * @param texts Text displayed with sign.
   * @return The block containing the sign.
   */
  public static Block createSign(World world, Location location, String[] texts) {
    var block = world.getBlockAt(location);
    block.setType(Material.BIRCH_SIGN, false);
    var signState = (Sign) block.getState();
    for (int i = 0; i < texts.length; i++) {
      var text = texts[i];
      signState.line(i, Component.text(text));
    }

    var rotData = (Rotatable) signState.getBlockData();
    // TODO this should point towards the spawn point
    rotData.setRotation(BlockFace.NORTH);
    signState.setBlockData(rotData);

    signState.update(true);
    return block;
  }

  /**
   * Applies general game settings to world.
   *
   * @param world Multiverse world to change.
   */
  public static void setCommonGameRulesForWorld(MultiverseWorld world) {
    // disable some gamerules for every world
    var gameRules = new String[]{"doDaylightCycle", "announceAdvancements", "doWeatherCycle",
        "fallDamage", "fireDamage", "freezeDamage", "doTraderSpawning", "doPatrolSpawning"};

    MinecraftLogger.info("Setting gamerules for world: " + world.getName());
    for (var gr : gameRules) {
      ServerUtils.dispatchCommand("mv gamerule %s false %s".formatted(gr, world.getName()));
    }
  }
}
