package com.microsoft.plaiground.gameserver.utils;

import com.microsoft.plaiground.client.model.BaseEvent;
import com.microsoft.plaiground.client.model.GameCompletionType;
import com.microsoft.plaiground.client.model.GameUpdate;
import com.microsoft.plaiground.client.model.PlatformGameEndEvent;
import com.microsoft.plaiground.client.model.PlatformGameStartEvent;
import com.microsoft.plaiground.client.model.PlatformPlayerJoinsGameEvent;
import com.microsoft.plaiground.client.model.PlatformPlayerTurnChangeEvent;
import com.microsoft.plaiground.client.model.PlatformTaskCompletedEvent;
import com.microsoft.plaiground.client.model.TurnChangeReason;
import com.microsoft.plaiground.common.config.CommonApplicationConfig;
import com.microsoft.plaiground.common.data.records.GameConfig;
import com.microsoft.plaiground.common.data.records.PlayerGameConfig;
import com.microsoft.plaiground.common.entities.GeometryInfo;
import com.microsoft.plaiground.common.providers.EventHubProducerClient;
import com.microsoft.plaiground.common.providers.JedisClientProvider;
import com.microsoft.plaiground.common.providers.PlaigroundServiceApi;
import com.microsoft.plaiground.common.utils.AsyncHelper;
import com.microsoft.plaiground.common.utils.LocationUtils;
import com.microsoft.plaiground.common.utils.MinecraftLogger;
import com.microsoft.plaiground.common.utils.PluginUtils;
import com.microsoft.plaiground.common.utils.ProxyUtils;
import com.microsoft.plaiground.common.utils.Scheduler;
import com.microsoft.plaiground.gameserver.constants.GameServerConstants;
import com.microsoft.plaiground.gameserver.entities.ActiveGameState;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Keeps track of which games are currently in process, their configuration (GameConfig) and status
 * (ActiveGameState), as well as information of the players that are playing the games
 * (PlayerGameConfig). It also keeps some other misc information about games: information about the
 * movement region of the players that have one
 */
public class GameTrackingHelper {

  private static final HashMap<UUID, PlayerGameConfig> playerIdToPlayerGameConfigs = new HashMap<UUID, PlayerGameConfig>();
  private static final HashMap<UUID, GeometryInfo> playerIdToMovementRegion = new HashMap<UUID, GeometryInfo>();
  private static final HashMap<UUID, Location> playerIdToLastKnownLocation = new HashMap<UUID, Location>();
  private static final HashMap<String, GameConfig> gameIdToGameConfig = new HashMap<String, GameConfig>();
  private static final HashMap<String, ActiveGameState> gameIdToActiveGameState = new HashMap<String, ActiveGameState>();
  private static String hashSalt = null;

  public static void initialize(CommonApplicationConfig appConfig) {
    hashSalt = appConfig.authenticationSettings().apiKey();
  }

  public static PlayerGameConfig getPlayerGameConfig(UUID playerId) {
    return playerIdToPlayerGameConfigs.get(playerId);
  }

  public static GameConfig getGameConfig(String gameId) {
    return gameIdToGameConfig.get(gameId);
  }

  public static GameConfig getGameConfigForPlayer(UUID playerId) {
    return gameIdToGameConfig.get(playerIdToPlayerGameConfigs.get(playerId).gameId);
  }

  public static ActiveGameState getActiveGameState(String gameId) {
    return gameIdToActiveGameState.get(gameId);
  }

  public static ActiveGameState getActiveGameStateForPlayer(UUID playerId) {
    var playerGameConfig = playerIdToPlayerGameConfigs.get(playerId);
    if (playerGameConfig == null) {
      return null;
    }

    return gameIdToActiveGameState.get(playerGameConfig.gameId);
  }

  public static Collection<ActiveGameState> getAllActiveGames() {
    return gameIdToActiveGameState.values();
  }

  /**
   * Send an event for the game when this event IS NOT associated with any specific player.
   * Appropriate when the event in question is a world or platform event.
   */
  public static void sendEventForGameId(BaseEvent event, String gameId) {
    EventHubProducerClient.sendGameEvent(
        event,
        getGameConfig(gameId),
        null // this event is not associated with a specific player
    );
  }

  /**
   * Send an event for a game when that event IS associated with a specific player.
   */
  public static void sendEventForPlayerId(BaseEvent event, UUID playerId) {
    EventHubProducerClient.sendGameEvent(
        event,
        getGameConfigForPlayer(playerId),
        getPlayerGameConfig(playerId)
    );
  }

  /**
   * Creates an entry for the game if there isn't one already, as well as an entry for the player
   * inside that game.
   *
   * If the game is new then a new GameStart event is sent. This method also sends a PlayerJoinGame
   * event.
   */
  public static void playerJoinsGame(
      UUID playerId,
      GameConfig gameConfig,
      PlayerGameConfig playerGameConfig,
      Location spawnLocation
  ) {
    // avoid doing anything if player is already registered in a game
    var existingPlayerGameConfig = GameTrackingHelper.getPlayerGameConfig(playerId);
    if (existingPlayerGameConfig != null) {
      MinecraftLogger.warning("Tried to register player with id " + playerId + " for a game with "
          + "id " + gameConfig.gameId + " for task " + gameConfig.taskId
          + " but the player is already "
          + "part of a game (game id: " + existingPlayerGameConfig.gameId + ")");
      return;
    }

    // If GameConfig for gameId does not already exist, this is the first player to join the game, start the game.
    var shouldStartGame = GameTrackingHelper.getGameConfig(playerGameConfig.gameId) == null;
    if (shouldStartGame) {
      GameTrackingHelper.gameStarts(playerGameConfig.gameId, gameConfig);
    }

    playerIdToPlayerGameConfigs.put(playerId, playerGameConfig);
    playerIdToLastKnownLocation.put(playerId, spawnLocation);
    var activeGameState = gameIdToActiveGameState.get(playerGameConfig.gameId);
    activeGameState.addPlayerAsRoleToGame(playerId, playerGameConfig.roleId);

    assert (hashSalt != null && !hashSalt.isEmpty()) :
        "Salt for hashing player id must be a non-empty string";

    var stringToHash = playerId.toString() + hashSalt;
    MessageDigest messageDigest;
    String playerIdHash;

    try {
      messageDigest = MessageDigest.getInstance("SHA-256");
      messageDigest.update(stringToHash.getBytes());
      playerIdHash = new String(messageDigest.digest());
    } catch (NoSuchAlgorithmException e) {
      MinecraftLogger.severe(e.toString());
      playerIdHash = UUID.randomUUID().toString();
    }

    var event = new PlatformPlayerJoinsGameEvent();
    event.setPlayerId(playerIdHash);
    event.setRoleId(playerGameConfig.roleId);
    event.setSpawnLocation(LocationUtils.convertToPlaigroundLocation(spawnLocation));

    GameTrackingHelper.sendEventForPlayerId(event, playerId);

    // If we just started the game, also send the first TurnChange event
    if (shouldStartGame) {
      var firstPlayerRole = activeGameState.getPlayerRoleAtIndex(0);
      var platformPlayerTurnChangeEvent = new PlatformPlayerTurnChangeEvent();
      platformPlayerTurnChangeEvent.setReason(TurnChangeReason.PLATFORM_GAME_START);
      // This is the start of Game and first turn. There is not a previous role id to set.
      platformPlayerTurnChangeEvent.setPreviousActiveRoleId(null);
      platformPlayerTurnChangeEvent.setNextActiveRoleId(firstPlayerRole.roleId);

      GameTrackingHelper.sendEventForPlayerId(platformPlayerTurnChangeEvent, playerId);
    }
  }

  public static void playerLeavesGame(UUID playerId) {
    var playerGameConfig = playerIdToPlayerGameConfigs.get(playerId);
    var activeGameState = gameIdToActiveGameState.get(playerGameConfig.gameId);
    activeGameState.removePlayerFromGame(playerGameConfig.roleId);

    playerIdToPlayerGameConfigs.remove(playerId);
  }


  /**
   * Creates an entry for the game and sends a PlatformGameStartEvent
   */
  private static void gameStarts(String gameId, GameConfig gameConfig) {
    gameIdToGameConfig.put(gameId, gameConfig);
    var activeGameState = new ActiveGameState(gameConfig);
    activeGameState.initializeRoleTurnOrder();
    gameIdToActiveGameState.put(gameId, activeGameState);

    var event = new PlatformGameStartEvent();
    GameTrackingHelper.sendEventForGameId(event, gameConfig.gameId);
  }

  /**
   * Effectively ends the game by recollecting all the resources related to the game, and sending
   * all players in the lobby, and deregister all agents.
   *
   * @param gameId ID of the game we want to end
   * @param endingPlayerId ID of the player that caused the game to end
   * @param gameCompletionType The reason why the game has ended
   */
  public static void gameEnds(
      String gameId,
      UUID endingPlayerId,
      GameCompletionType gameCompletionType
  ) {
    var gameConfig = gameIdToGameConfig.get(gameId);
    var playerGameConfig = playerIdToPlayerGameConfigs.get(endingPlayerId);

    // remove players from game world
    for (var playerInGameId : gameConfig.playerIdsInGame) {
      var playerInGameUUID = UUID.fromString(playerInGameId);
      var playerInGame = PluginUtils.getPlayer(playerInGameUUID);

      if (playerInGame != null) {
        ProxyUtils.sendPlayerToLobby(playerInGame);
      }
    }

    // deregister all agents in game
    for (var agentId : gameConfig.agentKeysInGame) {
      AgentManager.deregisterAgent(UUID.fromString(agentId));
    }

    // Send event signaling that the task was completed
    var platformTaskCompletedEvent = new PlatformTaskCompletedEvent();
    platformTaskCompletedEvent
        .setCompletionType(gameCompletionType);

    EventHubProducerClient.sendGameEvent(
        platformTaskCompletedEvent,
        gameConfig,
        playerGameConfig);

    AsyncHelper.run(() -> {
      // create new thread to update the completion type of the game in Service
      PlaigroundServiceApi
          .gamesApi()
          .updateGame(
              gameConfig.tournamentId,
              gameConfig.taskId,
              gameConfig.gameId,
              new GameUpdate() {{
                completionType(gameCompletionType);
              }});

      return null;
    });

    // clean up any in-memory related to the game resources that were being managed
    // by the GameTrackingHelper
    final GameConfig removedGameConfig = gameIdToGameConfig.remove(gameId);
    var removedPlayerGameConfigs = new ArrayList<PlayerGameConfig>();
    gameIdToActiveGameState.remove(gameId);

    if (removedGameConfig != null) {
      var allPlayerIds = new ArrayList<String>(
          removedGameConfig.playerIdsInGame.length + removedGameConfig.agentKeysInGame.length);

      Collections.addAll(allPlayerIds, removedGameConfig.playerIdsInGame);
      Collections.addAll(allPlayerIds, removedGameConfig.agentKeysInGame);

      for (var playerId : allPlayerIds) {
        var playerIdUUID = UUID.fromString(playerId);
        var removedPlayerGameConfig = playerIdToPlayerGameConfigs.remove(playerIdUUID);
        removedPlayerGameConfigs.add(removedPlayerGameConfig);
        playerIdToMovementRegion.remove(playerIdUUID);
        playerIdToLastKnownLocation.remove(playerIdUUID);
      }
    }

    // schedule cleanup of game world after a delay
    Scheduler.getInstance().scheduleOnceWithDelay(() -> {
      // delete game world after
      GameWorldUtils.deleteGameWorld(gameConfig.gameId);

      var jedis = JedisClientProvider.getInstance();

      // remove all player game configs from redis
      for (var playerGameConfigToDelete : removedPlayerGameConfigs) {
        if (playerGameConfigToDelete != null) {
          MinecraftLogger.info(
              "Deleting player game config for player: " + playerGameConfigToDelete.playerId);
          jedis.deleteRecord(playerGameConfigToDelete);
        }
      }

      // remove game config from redis
      if (removedGameConfig != null) {
        MinecraftLogger.info("Deleting game config: " + removedGameConfig.gameId);
        jedis.deleteRecord(removedGameConfig);
      }

      var gameEndEvent = new PlatformGameEndEvent();
      EventHubProducerClient.sendGameEvent(gameEndEvent, gameConfig, playerGameConfig);
    }, GameServerConstants.DELAY_BEFORE_DELETE_WORLD_AFTER_GAME_FINISH);
  }

  public static void setMovementRegionForPlayer(
      UUID playerId,
      GeometryInfo movementRegion
  ) {
    playerIdToMovementRegion.put(playerId, movementRegion);
  }

  public static Optional<GeometryInfo> getMovementRegionForPlayer(UUID playerId) {
    return Optional.ofNullable(playerIdToMovementRegion.get(playerId));
  }

  public static void setLastKnownLocationForPlayer(UUID playerId, Location location) {
    playerIdToLastKnownLocation.put(playerId, location);
  }

  public static Location getLastKnownLocationForPlayer(UUID playerId) {
    var lastLocation = playerIdToLastKnownLocation.get(playerId);

    assert lastLocation != null
        : "Tried to get last known location for player id %s but it was not found"
        .formatted(playerId);

    return playerIdToLastKnownLocation.get(playerId);
  }
}
