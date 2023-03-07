package com.microsoft.greenlands.gameserver.listeners;

import com.microsoft.greenlands.client.model.BlockPlaceEvent;
import com.microsoft.greenlands.client.model.BlockRemoveEvent;
import com.microsoft.greenlands.client.model.GameCompletionType;
import com.microsoft.greenlands.client.model.PlayerChatEvent;
import com.microsoft.greenlands.client.model.PlayerMoveEvent;
import com.microsoft.greenlands.client.model.PlayerState;
import com.microsoft.greenlands.client.model.PlayerToggleFlightEvent;
import com.microsoft.greenlands.common.constants.CommonConstants;
import com.microsoft.greenlands.common.providers.JedisClientProvider;
import com.microsoft.greenlands.common.providers.GreenlandsServiceApi;
import com.microsoft.greenlands.common.providers.TaskDataProvider;
import com.microsoft.greenlands.common.utils.AsyncHelper;
import com.microsoft.greenlands.common.utils.BlockUtils;
import com.microsoft.greenlands.common.utils.LocationUtils;
import com.microsoft.greenlands.common.utils.MinecraftLogger;
import com.microsoft.greenlands.common.utils.Scheduler;
import com.microsoft.greenlands.common.utils.WorldUtils;
import com.microsoft.greenlands.gameserver.constants.GameServerConstants;
import com.microsoft.greenlands.gameserver.utils.AgentManager;
import com.microsoft.greenlands.gameserver.utils.GameTrackingHelper;
import com.microsoft.greenlands.gameserver.utils.GameWorldUtils;
import com.microsoft.greenlands.gameserver.utils.PlayerRoleInGameUtils;
import net.kyori.adventure.text.TextComponent;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;


/**
 * The listeners in this class listen for events that we eventually want to track as happening as
 * part of a game. The events themselves can be validated and cancelled in the case that they are
 * invalid (for example, if a player's role doesn't allow them to place blocks, but they try to do
 * it regardless).
 */
public class GameListener implements Listener {

  /**
   * Get player role.
   **/
  private String getRoleName(Player player) {
    return GameTrackingHelper.getPlayerGameConfig(player.getUniqueId()).roleName;
  }


  /**
   * Check if world for game has already been created. If not then create it. Send gamer to game
   * world.
   */
  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerJoinEvent(PlayerJoinEvent eventIn) {
    var player = eventIn.getPlayer();
    // If player is Agent (represented as NPC), return
    if (player.hasMetadata("NPC")) {
      return;
    }

    player.sendMessage(
        CommonConstants.CHAT_COLOR_INFO + "Creating task world, this can take some time");

    WorldUtils.teleportPlayerToWorldSpawn(player, WorldUtils.TEST_WORLD_NAME);

    // remove all items from player (which might be left-overs from the last time they joined
    // this server).
    player.getInventory().clear();

    AsyncHelper.run(() -> {
      var jedis = JedisClientProvider.getInstance();

      var playerGameConfig = jedis.getPlayerGameConfig(player.getUniqueId());
      if (playerGameConfig.roleName.equals("test")) {

        return () -> Scheduler.getInstance()
            .executeWhenWorldReady(WorldUtils.TEST_WORLD_NAME, () -> {
              WorldUtils.teleportPlayerToWorldSpawn(player, WorldUtils.TEST_WORLD_NAME);
            });
      }

      var gameConfig = jedis.getGameConfig(playerGameConfig.gameId);

      try {
        MinecraftLogger.info(
            "New player %s (%s) participates in new game with ID %s, role %s, and using generator %s".formatted(
                player.getName(), player.getUniqueId(), playerGameConfig.gameId,
                playerGameConfig.roleName, gameConfig.generatorName));

        var taskInformation = GreenlandsServiceApi
            .tasksApi()
            .getTaskById(gameConfig.tournamentId, gameConfig.taskId);

        MinecraftLogger.info("Loading initial game state for task " + gameConfig.taskId);
        var initialGameState = TaskDataProvider.getInitialGameState(gameConfig.taskId);

        MinecraftLogger.info("Loading target game changes for task " + gameConfig.taskId);
        var targetGameChanges = TaskDataProvider.getTargetGameChanges(gameConfig.taskId);

        PlayerState initialPlayerState = null;
        var playerStates = initialGameState.getPlayerStates();
        if (playerStates != null) {
          initialPlayerState = playerStates.get(playerGameConfig.roleId);
        }

        var tournamentInfo = GreenlandsServiceApi
            .tournamentsApi()
            .getTournamentById(gameConfig.tournamentId);

        var roleInfo = tournamentInfo
            .getRoles().stream()
            .filter(role -> role.getId().equals(playerGameConfig.roleId))
            .findFirst()
            .get();

        final var finalTournament = tournamentInfo;
        final var finalTask = taskInformation;
        final var finalInitialGameState = initialGameState;
        final var finalInitialPlayerState = initialPlayerState;
        final var finalTargetGameChanges = targetGameChanges;
        final var finalRoleInfo = roleInfo;
        return () -> {
          // since server is single threaded we're guaranteed that: if this is not the first player
          // joining for this game then the game world has already been completely created.
          GameWorldUtils.createGameWorldIfNecessary(
              gameConfig,
              finalInitialGameState,
              finalInitialPlayerState,
              finalTargetGameChanges,
              finalTask
          );

          var worldName = GameWorldUtils.getGameWorldName(gameConfig.gameId);

          Scheduler.getInstance().executeWhenWorldReady(worldName, () -> {
            for (var agentKey : gameConfig.agentKeysInGame) {
              AgentManager.registerNewAgent(
                  agentKey,
                  gameConfig,
                  playerStates);
            }

            MinecraftLogger.info(
                "Teleporting player %s to new game world %s".formatted(player.getName(),
                    worldName));

            // ensure that if the role has a movement area constraint specified that we actually register
            // it, so we can enforce it
            var optMovementRegion = PlayerRoleInGameUtils.setMovementAreaForRole(
                player.getUniqueId(),
                gameConfig,
                finalInitialPlayerState);

            var spawnLocation = PlayerRoleInGameUtils.computeSpawnLocationForRole(
                worldName,
                finalInitialPlayerState,
                optMovementRegion.orElse(null));

            MinecraftLogger.info(
                "Spawning player for role: " + playerGameConfig.roleName + " at location: "
                    + spawnLocation);
            player.teleport(spawnLocation);

            // set player and game information in GameTrackingHelper
            GameTrackingHelper.playerJoinsGame(player.getUniqueId(),
                gameConfig,
                playerGameConfig,
                spawnLocation);

            // we need for the player to actually be in the world before we can set
            // properties for it
            Scheduler.getInstance().scheduleOnceWithDelay(() -> {
                  PlayerRoleInGameUtils.setPlayerRoleConfiguration(
                      player,
                      playerGameConfig,
                      finalInitialPlayerState);

                  // clear player chat history before sending welcome message
                  // Note: the fact that the chat log keeps the last 100
                  // messages was determined empirically
                  player.sendMessage(StringUtils.repeat(" \n", 100));

                  // show tournament and role instructions
                  player.sendMessage(ChatColor.GREEN + "----------------------------");
                  if (finalTournament.getInstructions() != null) {
                    player.sendMessage(ChatColor.YELLOW + "Tournament Instructions");
                    player.sendMessage(
                        ChatColor.RED + "Tournament: " + ChatColor.WHITE + tournamentInfo.getName());
                    player.sendMessage("");
                    player.sendMessage(tournamentInfo.getInstructions());
                    player.sendMessage(ChatColor.GREEN + "----------------------------");
                  }

                  player.sendMessage(
                      ChatColor.RED + "Current role: " + ChatColor.WHITE + finalRoleInfo.getName());
                  player.sendMessage("");
                  if (finalRoleInfo.getDescription() != null) {
                    player.sendMessage(ChatColor.YELLOW + "Role Instructions");
                    player.sendMessage(finalRoleInfo.getDescription());
                  }
                  player.sendMessage(ChatColor.GREEN + "----------------------------");
                },
                // end set player properties once in world
                GameServerConstants.DELAY_BEFORE_SET_PLAYER_PROPERTIES_IN_GAME);
          });
        };
      } catch (Exception error) {
        var gameWorldName = GameWorldUtils.getGameWorldName(gameConfig.gameId);
        MinecraftLogger.severe(
            "Error while attempting to prepare world: " + gameWorldName + "\n" + error.toString());
        WorldUtils.teleportPlayerToWorldSpawn(player, WorldUtils.LOBBY_WORLD_NAME);
        return null;
      }
    });
  }

  @EventHandler()
  public void onPlatformPlayerLeavesGameEvent(PlayerQuitEvent playerQuitEvent) {
    var playerId = playerQuitEvent.getPlayer().getUniqueId();

    // if a player leaves while they're in a game then we end that game
    var activeGameState = GameTrackingHelper.getActiveGameStateForPlayer(playerId);
    if (activeGameState != null && !activeGameState.isGameCompleted()) {
      activeGameState.endGameAndNotify(
          playerId,
          GameCompletionType.ABORT_PLAYER_LEAVE
      );
    }
  }

  @EventHandler
  public void onCommandPreProcess(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
    // don't allow players to use commands while in the loading world
    var player = playerCommandPreprocessEvent.getPlayer();
    if (!GameWorldUtils.isGameWorld(player.getWorld())) {
      player.sendMessage(
          "You attempted to use command '%s'. ".formatted(playerCommandPreprocessEvent.getMessage())
              +
              "This command will be ignored because it may only be used when in a game."
      );
      playerCommandPreprocessEvent.setCancelled(true);
      return;
    }
  }

  @EventHandler
  public void onBlockRemoveEvent(org.bukkit.event.block.BlockBreakEvent eventIn) {
    if (eventIn.isCancelled() || !GameWorldUtils.isGameWorld(eventIn.getPlayer().getWorld())) {
      return;
    }

    var playerConfig = GameTrackingHelper.getPlayerGameConfig(eventIn.getPlayer().getUniqueId());
    if (!playerConfig.canRemoveBlocks) {
      eventIn.setCancelled(true);
      return;
    }

    var event = new BlockRemoveEvent();

    event.setRoleId(getRoleName(eventIn.getPlayer()));
    event.setLocation(
        LocationUtils.convertToGreenlandsLocation(eventIn.getBlock().getLocation()));

    GameTrackingHelper.sendEventForPlayerId(event, eventIn.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onBlockPlaceEvent(org.bukkit.event.block.BlockPlaceEvent eventIn) {
    if (eventIn.isCancelled() || !GameWorldUtils.isGameWorld(eventIn.getPlayer().getWorld())) {
      return;
    }

    var playerConfig = GameTrackingHelper.getPlayerGameConfig(eventIn.getPlayer().getUniqueId());
    if (!playerConfig.canPlaceBlocks) {
      eventIn.setCancelled(true);
      return;
    }

    var event = new BlockPlaceEvent();

    event.setRoleId(getRoleName(eventIn.getPlayer()));
    event.setLocation(
        LocationUtils.convertToGreenlandsLocation(eventIn.getBlockPlaced().getLocation()));
    event.setMaterial(BlockUtils.MATERIAL_IDS.get(eventIn.getBlockPlaced().getType()));

    GameTrackingHelper.sendEventForPlayerId(event, eventIn.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onPlayerChatEvent(io.papermc.paper.event.player.AsyncChatEvent chatEvent) {
    if (chatEvent.isCancelled() || !GameWorldUtils.isGameWorld(chatEvent.getPlayer().getWorld())) {
      return;
    }

    var playerConfig = GameTrackingHelper.getPlayerGameConfig(chatEvent.getPlayer().getUniqueId());
    if (!playerConfig.canSendTextMessage) {
      chatEvent.setCancelled(true);
      return;
    }

    // Only players in the game world can view the chat messages
    var viewers = chatEvent.viewers();
    viewers.clear();
    for (var player : chatEvent.getPlayer().getWorld().getPlayers()) {
      viewers.add(player);
    }

    // Only current active player can send chat messages; abandon chat and send warning message, otherwise.
    var chatEventPlayer = chatEvent.getPlayer();
    var activeGameState = GameTrackingHelper.getActiveGameStateForPlayer(
        chatEventPlayer.getUniqueId());
    var currentPlayerId = activeGameState.getCurrentPlayerRole().playerId;
    if (!chatEventPlayer.getUniqueId().equals(currentPlayerId)) {
      chatEventPlayer.sendMessage("Please only send chat messages during your active turn.");
      chatEvent.setCancelled(true);
      return;
    }

    var playerChatEvent = new PlayerChatEvent();
    playerChatEvent.setRoleId(getRoleName(chatEvent.getPlayer()));
    playerChatEvent.setMessage(((TextComponent) chatEvent.message()).content());

    GameTrackingHelper.sendEventForPlayerId(playerChatEvent, chatEvent.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onPlayerMoveEvent(org.bukkit.event.player.PlayerMoveEvent playerMoveEvent) {
    if (playerMoveEvent.isCancelled()
        || !GameWorldUtils.isGameWorld(playerMoveEvent.getPlayer().getWorld())) {
      return;
    }

    var playerId = playerMoveEvent.getPlayer().getUniqueId();
    var playerConfig = GameTrackingHelper.getPlayerGameConfig(playerId);

    org.bukkit.Location playerNewLocation = playerMoveEvent.getTo();

    // prevent players from moving below Y==0 (underground)
    if (playerNewLocation.getY() < 0) {
      playerMoveEvent.setCancelled(true);
      return;
    }

    var optMovementRegion = GameTrackingHelper.getMovementRegionForPlayer(playerId);
    if (optMovementRegion.isPresent()) {
      var geometryInfo = optMovementRegion.get();

      // if a specific movement region is set, only allow player to move within that region
      if (!geometryInfo.isPointInXZColumnDefinedByGeometry(playerNewLocation.toVector())) {
        playerMoveEvent.setCancelled(true);
        return;
      }
    }

    var playerLastKnownLocation = GameTrackingHelper.getLastKnownLocationForPlayer(playerId);

    var playerMovedMoreThanLimit =
        Math.abs(Math.floor(playerNewLocation.getX()) - Math.floor(playerLastKnownLocation.getX()))
            >= GameServerConstants.PLAYER_MOVE_EVENT_MINIMUM_DISTANCE
            || Math.abs(
            Math.floor(playerNewLocation.getY()) - Math.floor(playerLastKnownLocation.getY()))
            >= GameServerConstants.PLAYER_MOVE_EVENT_MINIMUM_DISTANCE
            || Math.abs(
            Math.floor(playerNewLocation.getZ()) - Math.floor(playerLastKnownLocation.getZ()))
            >= GameServerConstants.PLAYER_MOVE_EVENT_MINIMUM_DISTANCE
            || Math.abs(playerNewLocation.getPitch() - playerLastKnownLocation.getPitch())
            >= GameServerConstants.PLAYER_MOVE_EVENT_MINIMUM_PITCH
            || Math.abs(playerNewLocation.getYaw() - playerLastKnownLocation.getYaw())
            >= GameServerConstants.PLAYER_MOVE_EVENT_MINIMUM_YAW;

    if (playerMovedMoreThanLimit) {
      GameTrackingHelper.setLastKnownLocationForPlayer(playerId, playerNewLocation);

      // Build event
      var event = new PlayerMoveEvent();

      event.setRoleId(getRoleName(playerMoveEvent.getPlayer()));
      event.setNewLocation(LocationUtils.convertToGreenlandsLocation(playerMoveEvent.getTo()));

      GameTrackingHelper.sendEventForPlayerId(event, playerId);
    }

  }

  @EventHandler
  public void onPlayerToggleFlightEvent(org.bukkit.event.player.PlayerToggleFlightEvent eventIn) {
    if (eventIn.isCancelled() || !GameWorldUtils.isGameWorld(eventIn.getPlayer().getWorld())) {
      return;
    }

    var playerGameConfig = GameTrackingHelper.getPlayerGameConfig(
        eventIn.getPlayer().getUniqueId());
    if (!playerGameConfig.canToggleFlight) {
      eventIn.setCancelled(true);
      return;
    }

    var event = new PlayerToggleFlightEvent();

    event.setRoleId(getRoleName(eventIn.getPlayer()));
    event.setIsFlying(eventIn.isFlying());

    GameTrackingHelper.sendEventForPlayerId(event, eventIn.getPlayer().getUniqueId());
  }
}