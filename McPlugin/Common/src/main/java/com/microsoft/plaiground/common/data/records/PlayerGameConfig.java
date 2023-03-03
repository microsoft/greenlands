package com.microsoft.plaiground.common.data.records;

import javax.annotation.Nullable;

import com.microsoft.plaiground.client.model.GameMode;
import com.microsoft.plaiground.client.model.TournamentRole;
import com.microsoft.plaiground.client.model.TurnLimits;
import com.microsoft.plaiground.common.data.RedisRecord;
import com.microsoft.plaiground.common.data.annotations.RedisKey;

public class PlayerGameConfig implements RedisRecord {

  @RedisKey
  public String playerId;

  public String gameId;
  public String roleId;
  public String roleName;

  public GameMode gameMode;

  // role capabilities
  public boolean canBeSeenByOtherPlayers;
  public boolean canSeeTargetGameState;

  // role actions
  public boolean canPlaceBlocks;
  public boolean canRemoveBlocks;
  public boolean canSendTextMessage;
  public boolean canEvaluate;
  public boolean canToggleFlight;

  // turn limits
  public @Nullable Integer maxTurnTimeSeconds;

  /**
   * Empty constructor required be Redis deserialization.
   */
  public PlayerGameConfig() {
  }

  /**
   * Constructs instance using only the required RedisKeys.
   */
  public PlayerGameConfig(String playerId) {
    this.playerId = playerId;
  }

  /**
   * Constructor for all properties.
   */
  public PlayerGameConfig(
      String playerId,
      String gameId,
      TournamentRole role,
      @Nullable TurnLimits turnLimits
  ) {
    this.playerId = playerId;
    this.gameId = gameId;
    this.roleId = role.getId();

    this.gameMode = role.getGameMode();
    this.roleName = role.getName();
    this.canBeSeenByOtherPlayers = role.getCapabilities().getCanBeSeenByOtherPlayers();
    this.canSeeTargetGameState = role.getCapabilities().getCanSeeTargetGameState();
    this.canPlaceBlocks = role.getActions().getCanPlaceBlocks();
    this.canRemoveBlocks = role.getActions().getCanRemoveBlocks();
    this.canSendTextMessage = role.getActions().getCanSendTextMessage();
    this.canEvaluate = role.getActions().getCanEvaluate();
    this.canToggleFlight = role.getActions().getCanToggleFlight();

    if (turnLimits != null) {
      this.maxTurnTimeSeconds = turnLimits.getMaxTimeOutSeconds();
    }
  }
}
