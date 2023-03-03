package com.microsoft.plaiground.gameserver.utils;

import com.microsoft.plaiground.client.model.AgentIsReadyEvent;
import com.microsoft.plaiground.client.model.BaseEvent;
import com.microsoft.plaiground.client.model.EventSource;
import com.microsoft.plaiground.client.model.PlayerState;
import com.microsoft.plaiground.common.data.records.GameConfig;
import com.microsoft.plaiground.common.entities.GeometryInfo;
import com.microsoft.plaiground.common.providers.JedisClientProvider;
import com.microsoft.plaiground.common.utils.AgentUtils;
import com.microsoft.plaiground.common.utils.MinecraftLogger;
import com.microsoft.plaiground.gameserver.entities.AgentBot;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

public class AgentManager {

  private final static HashMap<UUID, AgentBot> knownAgents = new HashMap<>();

  /**
   * Registers agent with the agent manager and spawns agent into world.
   *
   * This method will check Redis for a
   * {@link com.microsoft.plaiground.common.data.records.PlayerGameConfig} that corresponds to the
   * ID of the agent. If not found then this method will raise an {@link AssertionError}.
   */
  public static void registerNewAgent(
      String agentKey,
      GameConfig gameConfig,
      @Nullable Map<String, PlayerState> initialPlayerStates
  ) {
    var agentKeyUUID = UUID.fromString(agentKey);

    // avoid registering agents more than once
    if (getAgentByKey(agentKeyUUID).isPresent()) {
      return;
    }

    var jedisClient = JedisClientProvider.getInstance();
    var agentGameConfig = jedisClient.getPlayerGameConfig(
        agentKeyUUID
    );

    assert agentGameConfig != null :
        "Tried to register agent " + agentKeyUUID + " but this agent doesn't have a "
            + "game config entry in Redis!";

    MinecraftLogger.info("Registering new agent " + agentKeyUUID + " for game "
        + agentGameConfig.gameId);

    var expectedGameWorldName = GameWorldUtils.getGameWorldName(agentGameConfig.gameId);

    var agent = new AgentBot(
        agentKeyUUID,
        agentGameConfig,
        gameConfig);

    knownAgents.put(agentKeyUUID, agent);

    PlayerState agentInitialState = null;
    Optional<GeometryInfo> optMovementRegion = Optional.empty();
    if (initialPlayerStates != null) {
      agentInitialState = initialPlayerStates.get(agentGameConfig.roleId);
      if (agentInitialState != null) {
        optMovementRegion = PlayerRoleInGameUtils.setMovementAreaForRole(
            agentKeyUUID,
            gameConfig,
            agentInitialState
        );
      }
    }

    var spawnLocation = PlayerRoleInGameUtils.computeSpawnLocationForRole(
        expectedGameWorldName,
        agentInitialState,
        optMovementRegion.orElse(null));

    MinecraftLogger.info(
        "Spawning agent for role: " + agentGameConfig.roleName + " at location: " + spawnLocation);
    agent.spawnAtLocation(spawnLocation);

    GameTrackingHelper.playerJoinsGame(
        agentKeyUUID,
        gameConfig,
        agentGameConfig,
        spawnLocation
    );
  }

  /**
   * Destroys the NPC instance with the specified ID and removes it from the manager's records.
   */
  public static void deregisterAgent(UUID agentKey) {
    var agent = knownAgents.remove(agentKey);
    if (agent != null) {
      GameTrackingHelper.playerLeavesGame(agentKey);
      agent.destroy();
    }
  }

  /**
   * If the agent manager knows about the specified agent then returns an optional with an
   * {@link AgentBot}, otherwise it returns an empty optional
   */
  public static Optional<AgentBot> getAgentByKey(UUID agentKey) {
    return Optional.ofNullable(knownAgents.get(agentKey));
  }

  /**
   * Given an event emitted by an agent, this will check if the manger knows about the agent in
   * question, and if it does then it will route the event to the appropriate {@link AgentBot}
   * instance, so it can apply it.
   *
   * If the incoming event is *not* from an agent, or does *not* contain the agent's ID then this
   * will throw an {@link AssertionError}
   */
  public static void routeActionRequestToAgent(BaseEvent event) {
    assert event.getSource() == EventSource.AGENTSERVICE :
        "Tried to apply an action as an agent but the provided event does not come from an Agent, "
            + "it comes from: " + event.getSource();

    // ignore agent is ready events
    if (event instanceof AgentIsReadyEvent) {
      return;
    }

    assert (event.getRoleId() != null && !event.getRoleId().isEmpty()) :
        "Tried to apply an action as an agent but the provided event does not contain a roleId!";

    var agentKey = AgentUtils.getAgentKey(event.getGameId(), event.getRoleId());
    var agentBotOptional = getAgentByKey(agentKey);

    // If agentBot exists, attempt to enqueue the action
    // Otherwise, log a warning
    if (agentBotOptional.isPresent()) {
      // When agentKey is registered, check if the agentKey(PlayerId) is the current turn player
      var activeGameState = GameTrackingHelper.getActiveGameStateForPlayer(agentKey);
      var currentPlayerId = activeGameState.getCurrentPlayerRole().playerId;

      // If the agentKey is current turn player, the event/action will be processed;
      // otherwise, the action will be ignored and save a warning log.
      if (currentPlayerId.equals(agentKey)) {
        agentBotOptional.get().enqueueAction(event);
      } else {
        MinecraftLogger.warning(
            "Agent with agent key " + agentKey.toString()
                + " tried to apply an action " + event.getEventType()
                + " while it is not its turn. "
                + "This action will be ignored. Incoming action is for game: "
                + event.getGameId() + " and role: " + event.getRoleId());
      }
    } else {
      MinecraftLogger.warning(
          "We received an action from a bot that is not registered in the agent "
              + "manager! This action will be ignored. Incoming action is for game: "
              + event.getGameId() + " and role: " + event.getRoleId());
    }
  }

  public static Collection<AgentBot> getAllKnownAgents() {
    return knownAgents.values();
  }
}
