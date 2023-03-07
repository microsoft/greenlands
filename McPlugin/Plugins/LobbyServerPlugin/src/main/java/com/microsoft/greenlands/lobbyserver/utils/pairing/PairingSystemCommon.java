package com.microsoft.greenlands.lobbyserver.utils.pairing;

import com.microsoft.greenlands.client.model.Game;
import com.microsoft.greenlands.client.model.PlaiGroundTask;
import com.microsoft.greenlands.client.model.Tournament;
import com.microsoft.greenlands.client.model.TournamentRole;
import com.microsoft.greenlands.client.model.TurnLimits;
import com.microsoft.greenlands.common.data.records.GameConfig;
import com.microsoft.greenlands.common.data.records.PlayerGameConfig;
import com.microsoft.greenlands.common.enums.ChallengeType;
import com.microsoft.greenlands.common.providers.JedisClientProvider;
import com.microsoft.greenlands.common.providers.GreenlandsServiceApi;
import com.microsoft.greenlands.common.providers.TaskDataProvider;
import com.microsoft.greenlands.common.utils.AgentUtils;
import com.microsoft.greenlands.common.utils.AsyncHelper;
import com.microsoft.greenlands.common.utils.PluginUtils;
import com.microsoft.greenlands.common.utils.ProxyUtils;
import com.microsoft.greenlands.common.utils.Scheduler;
import com.microsoft.greenlands.lobbyserver.entities.PlayerPairingInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

class PairingSystemCommon {

  /**
   * Given a {@link Tournament}, {@link PlaiGroundTask}, and list of players that will play in that
   * task, this method will: randomly assign roles to the players (unless the role assignment
   * parameter is set), create an actual {@link Game} instance on Service, create entries in Redis
   * for the game ({@link GameConfig}) as well as the {@link PlayerGameConfig} for each player, and
   * finally send players to the game server, so they can start playing the game.
   *
   * If the optional role assignments don't include a role for every player then the players with a
   * "desired role" will be randomly assigned one of the remaining roles.
   */
  protected static void createGameForPlayersAndMoveThemToGameServer(
      List<PlayerPairingInfo> playerPairingInfos,
      PlaiGroundTask task,
      Tournament tournament,
      @Nullable ChallengeType challengeType,
      @Nullable String challengeId,
      @Nullable Map<UUID, TournamentRole> partialDesiredRoleAssignments
  ) {
    HashMap<TournamentRole, PlayerPairingInfo> roleAssignments = new HashMap<>();

    if (partialDesiredRoleAssignments != null) {
      for (var entry : partialDesiredRoleAssignments.entrySet()) {
        var playerId = entry.getKey();
        var role = entry.getValue();

        roleAssignments.put(
            role,
            playerPairingInfos.stream()
                .filter(playerPairingInfo -> playerPairingInfo.playerId().equals(playerId))
                .findFirst()
                .orElseThrow()
        );
      }

    }

    // randomly assign remaining roles for all players that don't already have a role assigned
    var unassignedPlayers = new HashSet<>(playerPairingInfos);
    unassignedPlayers.removeAll(roleAssignments.values());

    var unassignedRoles = new HashSet<>(tournament.getRoles());
    unassignedRoles.removeAll(roleAssignments.keySet());

    var remainingRoleAssignments = getRandomRoleAssignmentsForPlayers(
        unassignedRoles,
        unassignedPlayers
    );

    roleAssignments.putAll(remainingRoleAssignments);

    var game = GreenlandsServiceApi.gamesApi().createGame(
        tournament.getId(),
        task.getId()
    );

    createJedisEntries(
        roleAssignments,
        tournament,
        task,
        game,
        challengeType,
        challengeId
    );

    AsyncHelper.runOnMainThread(() -> {
      for (var gameParticipantPlayerPairingInfo : playerPairingInfos) {
        // Only send Human players to the Game server. Agent players do not really exist outside of the Game Server
        if (gameParticipantPlayerPairingInfo.isAgent() == false) {
          var player = PluginUtils.getPlayer(gameParticipantPlayerPairingInfo.playerId());
          ProxyUtils.sendPlayerToGameServer(player);
        }
      }
    });
  }

  /**
   * Given a list of {@link TournamentRole}s and {@link PlayerPairingInfo}s (which should be of the
   * same size), returns a HashMap that represents random pairings of players to roles.
   *
   * If the lists of roles and players are not the same size then an {@link AssertionError} will be
   * thrown.
   */
  private static HashMap<TournamentRole, PlayerPairingInfo> getRandomRoleAssignmentsForPlayers(
      Collection<TournamentRole> roles,
      Collection<PlayerPairingInfo> playerPairingInfos
  ) {
    assert playerPairingInfos.size() == roles.size() :
        "The number of players (%d) selected for the game is different from the number of roles in tournament (%d)".formatted(
            playerPairingInfos.size(), roles.size());

    var playerPairingInfoList = playerPairingInfos.stream().toList();

    // create copy of roles, so we don't modify the input role list
    var rolesToAssign = new ArrayList<>(roles);
    Collections.shuffle(rolesToAssign);

    var tournamentRoleToPlayerId = new HashMap<TournamentRole, PlayerPairingInfo>();

    for (int i = 0; i < playerPairingInfos.size(); i++) {
      tournamentRoleToPlayerId.put(rolesToAssign.get(i), playerPairingInfoList.get(i));
    }

    return tournamentRoleToPlayerId;
  }


  /**
   * Adds the information necessary to represent a game instance to Jedis, so that it can easily be
   * retrieved by the game server when creating the game.
   */
  private static void createJedisEntries(
      HashMap<TournamentRole, PlayerPairingInfo> roleToPlayerPairingInfo,
      Tournament tournament,
      PlaiGroundTask task,
      Game game,
      @Nullable ChallengeType challengeType,
      @Nullable String challengeId
  ) {
    var jedis = JedisClientProvider.getInstance();
    String gameGroupId = null;

    // save all player game configs
    for (var kvp : roleToPlayerPairingInfo.entrySet()) {
      var playerPairingInfo = kvp.getValue();
      var tournamentRole = kvp.getKey();

      // if there's a player with a group id then we set that as the game's GroupId. We assume
      // every player in the game either didn't specify a group id code or joined the game with the
      // same code
      if (gameGroupId == null && playerPairingInfo.groupId() != null) {
        gameGroupId = playerPairingInfo.groupId();
      }

      TurnLimits turnLimits = null;
      var roleIdToTurnLimits = task.getTurnLimits();
      if (roleIdToTurnLimits != null) {
        turnLimits = roleIdToTurnLimits.get(tournamentRole.getId());
      }

      var playerGameConfig = new PlayerGameConfig(
          playerPairingInfo.playerId().toString(),
          game.getId(),
          tournamentRole,
          turnLimits
      );

      // if the player we're creating an entry for is an Agent then be sure to use it's AgentKey instead
      // of the agent service ID!
      if (playerPairingInfo.isAgent()) {
        playerGameConfig.playerId = AgentUtils.getAgentKey(
            game.getId(),
            tournamentRole.getId()
        ).toString();
      }

      jedis.saveRecordWithExpiration(playerGameConfig, 4 * Scheduler.HOUR_MS);
    }

    var initialGameState = TaskDataProvider.getInitialGameState(task.getId());
    var generatorName = initialGameState
        .getWorldState()
        .getGeneratorName();

    var humanPlayersIds = roleToPlayerPairingInfo
        .values().stream()
        .filter(playerPairingInfo -> !playerPairingInfo.isAgent())
        .map(PlayerPairingInfo::playerId)
        .map(UUID::toString)
        .toList()
        .toArray(new String[0]);

    var agentKeysInGame = roleToPlayerPairingInfo
        .entrySet().stream()
        .filter(entry -> entry.getValue().isAgent())
        .map(entry -> AgentUtils.getAgentKey(
            game.getId(),
            entry.getKey().getId()))
        .map(UUID::toString)
        .toList()
        .toArray(new String[0]);

    var agentServiceIdsInGame = roleToPlayerPairingInfo
        .values().stream()
        .filter(PlayerPairingInfo::isAgent)
        .map(playerPairingInfo -> playerPairingInfo.playerId().toString())
        .toList()
        .toArray(new String[0]);

    // save game config
    jedis.saveRecordWithExpiration(
        new GameConfig(game.getId(),
            task.getId(),
            challengeId,
            challengeType,
            tournament.getId(),
            generatorName,
            humanPlayersIds,
            agentKeysInGame,
            agentServiceIdsInGame,
            gameGroupId,
            task.getGameLimits()
        ),
        4 * Scheduler.HOUR_MS
    );
  }
}
