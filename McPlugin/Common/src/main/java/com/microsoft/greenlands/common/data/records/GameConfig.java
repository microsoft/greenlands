package com.microsoft.greenlands.common.data.records;

import com.microsoft.greenlands.client.model.GameLimits;
import com.microsoft.greenlands.common.data.RedisRecord;
import com.microsoft.greenlands.common.data.annotations.RedisKey;
import com.microsoft.greenlands.common.enums.ChallengeType;
import javax.annotation.Nullable;

public class GameConfig implements RedisRecord {

  @RedisKey
  public String gameId;

  public String taskId;
  public @Nullable String challengeId;
  public @Nullable ChallengeType challengeType;
  public String tournamentId;
  public String generatorName;
  public String[] playerIdsInGame;
  public @Nullable String groupId;
  public @Nullable Integer maxTimeOutSeconds;
  public @Nullable Integer maxTurnLimit;

  /**
   * The keys that identify the agents in the current game
   */
  public String[] agentKeysInGame;

  /**
   * The IDs of the agent services in the current game
   */
  public String[] agentServiceIdsInGame;

  /**
   * Empty constructor required be Redis deserialization.
   */
  public GameConfig() {
  }

  /**
   * Constructs instance using only the required RedisKeys.
   */
  public GameConfig(String gameId) {
    this.gameId = gameId;
  }

  /**
   * Constructor for all properties.
   */
  public GameConfig(
      String gameId,
      String taskId,
      @Nullable String challengeId,
      @Nullable ChallengeType challengeType,
      String tournamentId,
      String generatorName,
      String[] playerIdsInGame,
      String[] agentKeysInGame,
      String[] agentServiceIdsInGame,
      @Nullable String groupId,
      @Nullable GameLimits gameLimits
  ) {
    this.gameId = gameId;
    this.taskId = taskId;
    this.challengeId = challengeId;
    this.challengeType = challengeType;
    this.tournamentId = tournamentId;
    this.generatorName = generatorName;
    this.playerIdsInGame = playerIdsInGame;

    assert agentKeysInGame.length == agentServiceIdsInGame.length :
        "There should be the same number of agent service IDs as there are agent keys! There were "
            + "%d agent service IDs and %d agent keys.".formatted(
            agentServiceIdsInGame.length,
            agentKeysInGame.length);

    this.agentKeysInGame = agentKeysInGame;
    this.agentServiceIdsInGame = agentServiceIdsInGame;

    this.groupId = groupId;

    if (gameLimits != null) {
      this.maxTimeOutSeconds = gameLimits.getMaxTimeOutSeconds();
      this.maxTurnLimit = gameLimits.getMaxTurnLimit();
    }
  }
}
