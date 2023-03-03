package com.microsoft.plaiground.lobbyserver.utils.pairing;

import com.microsoft.plaiground.client.model.AgentIsReadyEvent;
import com.microsoft.plaiground.client.model.AgentService;
import com.microsoft.plaiground.client.model.TournamentRole;
import com.microsoft.plaiground.common.constants.CommonConstants;
import com.microsoft.plaiground.common.enums.ChallengeType;
import com.microsoft.plaiground.common.providers.PlaigroundServiceApi;
import com.microsoft.plaiground.common.utils.AsyncHelper;
import com.microsoft.plaiground.common.utils.MinecraftLogger;
import com.microsoft.plaiground.common.utils.PluginUtils;
import com.microsoft.plaiground.lobbyserver.entities.PlayerPairingInfo;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The agent pairing system keeps a list of how many instances of a given Agent Service are
 * available to play. A human player can then request to play a game with one of these instances,
 * which will cause it to be removed from the pairing system, a game instance created for the
 * specified task, and both player and agent instance being sent over to the game server.
 *
 * All methods in this pairing system are meant to be executed from the main server thread.
 */
public class AgentPairingSystem {

  private static final HashMap<String, Queue<AgentService>> agentsReadyForGame = new HashMap<>();

  /**
   * Given an {@link AgentIsReadyEvent}, create a new instance of the agent that sent this event in
   * the pairing system.
   */
  public static void registerAgent(AgentService agentService, AgentIsReadyEvent agentIsReadyEvent) {
    // don't add new agent to queue if we've already hit the specified maximum number of games
    var maxGames = agentIsReadyEvent.getMaxGames();
    assert maxGames != null
        : "AgentIsReadyEvent must specify a maximum number of games that can be played with the agent";

    var queueOfRegisteredInstances = agentsReadyForGame.get(agentService.getId());
    var currentNumOfRegisteredInstances =
        queueOfRegisteredInstances != null
            ? queueOfRegisteredInstances.size()
            : 0;

    if (currentNumOfRegisteredInstances >= maxGames) {
      MinecraftLogger.warning(
          "Tried to register a new agent instance to the agent pairing queue but"
              + " the maximum number instances (" + maxGames
              + ") for that agent has already been reached. Request will "
              + "be ignored. Agent service id: "
              + agentService.getId()
      );
      return;
    }

    registerAgentPairingInfo(agentService);
  }

  /**
   * This method will try to pair the specified player with the specified agent to play on the
   * specified task. If there is no instance of the agent available then the player is informed of
   * the situation and nothing else is done.
   */
  public static void requestGameWithAgent(
      PlayerPairingInfo playerPairingInfo,
      String agentId,
      String tournamentId,
      String challengeId,
      String taskId
  ) {
    if (!agentsReadyForGame.containsKey(agentId)) {
      PluginUtils
          .getPlayer(playerPairingInfo.playerId())
          .sendMessage("The requested agent is not currently available to play any games");
      return;
    }

    // retrieve a reference to the agent's pairing info instance we'll use
    // and remove it from that agent's queue
    var agentService = agentsReadyForGame
        .get(agentId)
        .poll();

    assert agentService != null :
        "Agent pairing info is null even though we have an entry for the agent registered as ready for games.";

    // if the agent's queue is now empty then do some housekeeping
    if (agentsReadyForGame.get(agentId).isEmpty()) {
      agentsReadyForGame.remove(agentId);
    }

    PluginUtils
        .getPlayer(playerPairingInfo.playerId())
        .sendMessage(
            CommonConstants.CHAT_COLOR_INFO + "We're trying to pair you with an agent"
        );

    AsyncHelper.run(() -> {
      // check if components really exists in Service
      var tournament = PlaigroundServiceApi
          .tournamentsApi()
          .getTournamentById(tournamentId);

      var task = PlaigroundServiceApi
          .tasksApi()
          .getTaskById(tournamentId, taskId);

      var agentChallenge = PlaigroundServiceApi
          .agentChallengesApi()
          .getAgentChallengeById(tournamentId, challengeId);

      if (tournament == null || agentChallenge == null || task == null) {
        return () -> {
          MinecraftLogger.severe(
              ("Player tried to create game with agent '%s' but tournament (id '%s'), challenge "
                  + "(id '%s'), or task (id '%s') does not exist").formatted(
                  agentId, tournamentId, agentService.getAgentChallengeId(), taskId));

          var player = PluginUtils.getPlayer(playerPairingInfo.playerId());
          player.sendMessage(
              "Could not join game with agent because the provided task ID is not valid"
          );

          // if there was an error then re-queue the agent pairing info
          registerAgentPairingInfo(agentService);
        };
      }

      if (!challengeId.equalsIgnoreCase(agentService.getAgentChallengeId())) {
        return () -> {
          MinecraftLogger.severe(
              "Player tried to create game with agent '%s' but the challenge ID provided "
                  + "does not match the challenge ID of the agent".formatted(
                  agentId, challengeId));

          var player = PluginUtils.getPlayer(playerPairingInfo.playerId());
          player.sendMessage(
              "Could not join game with agent because the provided challenge ID is not valid"
          );

          registerAgentPairingInfo(agentService);
        };
      }

      if (tournament.getRoles().size() != 2) {
        return () -> {
          MinecraftLogger.severe(
              "Tried to pair a player and an agent for a tournament that does not have exactly "
                  + "two roles! Tournament id: " + tournamentId
          );

          var player = PluginUtils.getPlayer(playerPairingInfo.playerId());
          player.sendMessage(
              "Could not join game with agent because the tournament does not have exactly two roles"
          );

          registerAgentPairingInfo(agentService);
        };
      }

      var roleIdToRole = tournament
          .getRoles().stream()
          .collect(Collectors.toMap(TournamentRole::getId, Function.identity()));

      var roleForAgent = roleIdToRole.remove(
          agentChallenge.getTournamentRoleIdSupported()
      );

      var agentUuid = UUID.fromString(agentId);
      var desiredRoleAssignments = Map.of(
          agentUuid, roleForAgent
      );

      var agentPairingInfo = new PlayerPairingInfo(
          UUID.fromString(agentService.getId()),
          true,
          null,
          null
      );

      PairingSystemCommon.createGameForPlayersAndMoveThemToGameServer(
          List.of(agentPairingInfo, playerPairingInfo),
          task,
          tournament,
          ChallengeType.AGENT_CHALLENGE,
          agentChallenge.getId(),
          desiredRoleAssignments
      );

      return null;
    });

  }

  private static void registerAgentPairingInfo(AgentService agentService) {
    var agentId = agentService.getId();

    if (!agentsReadyForGame.containsKey(agentId)) {
      agentsReadyForGame.put(agentId, new LinkedList<>());
    }

    agentsReadyForGame.get(agentId).add(agentService);
    var availableGames = agentsReadyForGame.get(agentId).size();
    MinecraftLogger.info("Added agent " + agentService.getId() + " to queue. " + availableGames
        + " agent instances are available");
  }
}
