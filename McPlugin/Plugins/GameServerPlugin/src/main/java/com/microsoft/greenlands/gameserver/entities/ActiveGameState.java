package com.microsoft.greenlands.gameserver.entities;

import com.microsoft.greenlands.client.model.GameCompletionType;
import com.microsoft.greenlands.client.model.PlatformPlayerTurnChangeEvent;
import com.microsoft.greenlands.client.model.TurnChangeReason;
import com.microsoft.greenlands.common.constants.CommonConstants;
import com.microsoft.greenlands.common.data.records.GameConfig;
import com.microsoft.greenlands.common.data.records.PlayerGameConfig;
import com.microsoft.greenlands.common.enums.ChallengeType;
import com.microsoft.greenlands.common.providers.EventHubProducerClient;
import com.microsoft.greenlands.common.providers.GreenlandsServiceApi;
import com.microsoft.greenlands.common.utils.MinecraftLogger;
import com.microsoft.greenlands.common.utils.PluginUtils;
import com.microsoft.greenlands.common.utils.ServerUtils;
import com.microsoft.greenlands.common.utils.TextUtils;
import com.microsoft.greenlands.gameserver.utils.AgentManager;
import com.microsoft.greenlands.gameserver.utils.GameTrackingHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Maintain information about active games such as whose turn it is, how the game was ended, etc.
 */

public class ActiveGameState {

  private GameConfig gameConfig;
  private Instant gameStartTimeUTC;
  private Instant turnStartTimeUTC;
  private int countOfTurnsTaken;
  private int activePlayerRoleOffset;
  private List<PlayerRole> playerRolesInTurnOrder;

  // to be used internally to track if the game has finished or not, so that we
  // can prevent re-executing the "game end" logic if it has already finished.
  // Once a game is finished it should get cleaned up by the garbage collector.
  private @Nullable GameCompletionType gameCompletionType;

  public ActiveGameState(GameConfig gameConfig) {
    this.gameConfig = gameConfig;
    this.gameStartTimeUTC = Instant.now();
    this.turnStartTimeUTC = Instant.now();
    this.countOfTurnsTaken = 0;
    // In the normal case, activePlayerRoleOffset will equal countOfTurnsTaken.
    // However, because we are using offset of list to identify the active player,
    // when players leave the game it may cause association between player and
    // indices to shift.
    // Example: Assume game with total players [A, B, C, D, E] who have all joined
    // the game. The current turns/offset is 2 meaning C is the active player.
    // If player B leaves the game, the list of players becomes [A, C, D, E]
    // If we preserve the offset of 2, the active player will be D. However, we want
    // the active player to be C. This is why we decrement the offset to preserve
    // the active player.
    this.activePlayerRoleOffset = 0;
    this.playerRolesInTurnOrder = new ArrayList<PlayerRole>();
  }

  public void takeTurn() {
    // If we have reached the maximum number of turns, then end the game.
    if (gameConfig.maxTurnLimit != null && countOfTurnsTaken >= gameConfig.maxTurnLimit) {
      var activePlayerRole = getCurrentPlayerRole();
      this.endGameAndNotify(activePlayerRole.playerId, GameCompletionType.ABORT_MAX_TURN);
    }

    var previousActivePlayerRole = getCurrentPlayerRole();

    countOfTurnsTaken += 1;
    activePlayerRoleOffset += 1;
    turnStartTimeUTC = Instant.now();

    var nextActivePlayerRole = getCurrentPlayerRole();
    var agentBotOptional = AgentManager.getAgentByKey(nextActivePlayerRole.playerId);
    if (agentBotOptional.isPresent()) {
      agentBotOptional.get().allowAdditionalActions();
    }

    var previousPlayerName = getPlayerName(previousActivePlayerRole.playerId);
    var previousPlayerGameConfig = GameTrackingHelper.getPlayerGameConfig(
        previousActivePlayerRole.playerId);
    assert previousPlayerGameConfig != null
        : "previousPlayerGameConfig should not be null when we advance the turn to the next player";

    var nextPlayerName = getPlayerName(nextActivePlayerRole.playerId);
    var nextPlayerGameConfig = GameTrackingHelper.getPlayerGameConfig(
        nextActivePlayerRole.playerId);
    assert nextPlayerGameConfig != null
        : "nextPlayerGameConfig should not be null when we advance the turn to the next player";

    var turnAdvanceMessage =
        "Player " + previousPlayerName + "'s turn as role " + previousPlayerGameConfig.roleName
            + " has ended. Next turn is " + nextPlayerName + "'s turn as role "
            + nextPlayerGameConfig.roleName;

    for (var playerId : gameConfig.playerIdsInGame) {
      var playerInGameUUID = UUID.fromString(playerId);
      var playerInGame = PluginUtils.getPlayer(playerInGameUUID);
      if (playerInGame != null) {
        playerInGame.sendMessage(CommonConstants.CHAT_COLOR_INFO + turnAdvanceMessage);
      }
    }

    MinecraftLogger.info("Advancing turn for game " + gameConfig.gameId);
  }

  public void endGameIfOverMaxTime() {
    if (this.isGameCompleted()) {
      MinecraftLogger.warning(
          "Tried to end game %s because it's over time but game has already been completed. This will be ignored."
              .formatted(this.gameConfig.gameId));
      return;
    }

    if (gameConfig.maxTimeOutSeconds == null) {
      return;
    }

    var gameDurationSeconds = ChronoUnit.SECONDS.between(gameStartTimeUTC, Instant.now());
    if (gameDurationSeconds >= (long) gameConfig.maxTimeOutSeconds) {
      var activePlayerRole = getCurrentPlayerRole();
      this.endGameAndNotify(activePlayerRole.playerId, GameCompletionType.ABORT_TIME_OUT);
    }
  }

  public void advanceTurnIfOverMaxTime() {
    if (this.isGameCompleted()) {
      MinecraftLogger.warning(
          "Tried to advance turn because of timeout in game %s but game has already been completed. This will be ignored."
              .formatted(this.gameConfig.gameId));
      return;
    }

    var activePlayerRole = getCurrentPlayerRole();
    var playerGameConfig = GameTrackingHelper.getPlayerGameConfig(activePlayerRole.playerId);
    assert playerGameConfig != null
        : "playerGameConfig should not be null when we check if the current turn's duration is over the role's turn time limit";

    if (playerGameConfig.maxTurnTimeSeconds != null) {
      var turnTimeSeconds = ChronoUnit.SECONDS.between(turnStartTimeUTC, Instant.now());
      if (turnTimeSeconds >= (long) playerGameConfig.maxTurnTimeSeconds) {
        this.endTurn(playerGameConfig, TurnChangeReason.ABORT_TIME_OUT);
      }
    }
  }

  // Download role order from tournament definition and save as player roles
  // objects to associate player id with role id
  public void initializeRoleTurnOrder() {
    var roleIdsInTurnOrder = getRoleIdsInTurnOrder();

    var playerRolesInTurnOrder = roleIdsInTurnOrder
        .stream()
        .map(roleId -> new PlayerRole(roleId))
        .collect(Collectors.toList());

    this.playerRolesInTurnOrder = playerRolesInTurnOrder;
  }

  private List<String> getRoleIdsInTurnOrder() {
    var tournamentInfo = GreenlandsServiceApi
        .tournamentsApi()
        .getTournamentById(gameConfig.tournamentId);

    var roleIdsInTurnOrder = new ArrayList<String>();

    for (var role : tournamentInfo.getRoles()) {
      roleIdsInTurnOrder.add(role.getId());
    }

    return roleIdsInTurnOrder;
  }

  private List<PlayerRole> getPlayerRolesInGame() {
    return playerRolesInTurnOrder
        .stream()
        .filter(playerRole -> playerRole.isPlayerPresentInGame())
        .collect(Collectors.toList());
  }

  public void addPlayerAsRoleToGame(UUID playerId, String roleId) {
    for (var playerRole : playerRolesInTurnOrder) {
      if (playerRole.roleId.equals(roleId)) {
        playerRole.playerId = playerId;
        return;
      }
    }

    MinecraftLogger.warning("Attempted to add player " + playerId + " as role " + roleId
        + " to game " + gameConfig.gameId + " but role was not found in game.");
  }

  public void removePlayerFromGame(String roleIdToRemove) {
    var activePlayerRole = getCurrentPlayerRole();
    var activePlayerRoleIndex = playerRolesInTurnOrder.indexOf(activePlayerRole);
    var playerRoleIndex = 0;

    for (var playerRole : playerRolesInTurnOrder) {
      if (playerRole.roleId.equals(roleIdToRemove)) {
        // Setting playerId to null, implicitly removes the player from current game
        playerRole.playerId = null;

        if (playerRoleIndex <= activePlayerRoleIndex) {
          // If the player removed is located before or active player or is the active
          // player, decrement the active player offset to compensate for index shift
          activePlayerRoleOffset -= 1;
        }

        // If player removed is also the active player, then assume their turn is over
        if (activePlayerRole.roleId.equals(roleIdToRemove)) {
          // takeTurn();
        }

        break;
      }

      playerRoleIndex += 1;
    }
  }

  public PlayerRole getCurrentPlayerRole() {
    return getPlayerRoleAtOffset(0);
  }

  // Given an offset from the current active role, return the player role at that
  // offset
  public PlayerRole getPlayerRoleAtOffset(int offset) {
    var adjustedOffset = activePlayerRoleOffset + offset;
    var playerRoleIndex = adjustedOffset % playerRolesInTurnOrder.size();
    var playerRolesInGame = getPlayerRolesInGame();

    return _getPlayerRoleAtIndex(playerRolesInGame, playerRoleIndex);
  }

  // Given an index, return the player role at the index regardless if the player
  // for that role is in the game
  public PlayerRole getPlayerRoleAtIndex(int playerRoleIndex) {
    return _getPlayerRoleAtIndex(this.playerRolesInTurnOrder, playerRoleIndex);
  }

  private PlayerRole _getPlayerRoleAtIndex(List<PlayerRole> playerRoles, int playerRoleIndex) {
    if (playerRoleIndex > playerRoles.size() - 1) {
      throw new RuntimeException(
          "You attempted to access the player role at index " + playerRoleIndex
              + " but there are only " + playerRoles.size() + " players in the game.");
    }

    var playerRole = playerRoles.get(playerRoleIndex);

    return playerRole;
  }

  public boolean isGameCompleted() {
    return this.gameCompletionType != null;
  }

  /**
   * Cleans up all game resources and notifies the players that the game has ended and why.
   *
   * @param playerId ID of the player that caused the game to end
   * @param gameCompletionType The reason why the game has ended
   */
  public void endGameAndNotify(UUID playerId, GameCompletionType gameCompletionType) {
    // don't end the game if we had already ended it in the past
    if (this.isGameCompleted()) {
      MinecraftLogger.warning(
          "Tried to end game %s but game had already been completed. This will be ignored."
              .formatted(this.gameConfig.gameId)
      );
      return;
    }

    this.gameCompletionType = gameCompletionType;

    var playerGameConfig = GameTrackingHelper.getPlayerGameConfig(playerId);
    assert playerGameConfig != null :
        "playerGameConfig should not be null when we try to finish a game";

    assert gameConfig != null :
        "gameConfig should not be null when we try to finish a game";

    MinecraftLogger.info(
        "Game " + gameConfig.gameId + " has ended with reason " + gameCompletionType);

    var endGameMessage = getEndGameMessage(
        UUID.fromString(playerGameConfig.playerId),
        gameCompletionType);

    for (var playerInGameId : gameConfig.playerIdsInGame) {
      var playerInGameUUID = UUID.fromString(playerInGameId);
      var playerInGame = PluginUtils
          .getPlayer(playerInGameUUID);

      if (playerInGame != null) {
        playerInGame.sendMessage(CommonConstants.CHAT_COLOR_INFO + endGameMessage);
        sendConfirmationCodeToPlayer(playerInGame, gameConfig, playerGameConfig);
      }
    }

    GameTrackingHelper.gameEnds(gameConfig.gameId, playerId, gameCompletionType);
  }

  /**
   * Gets the message that should be sent to all players when a game ends.
   *
   * The message differs depending on what the {@link GameCompletionType} is, as well as whether the
   * player id represents an agent or a human player.
   *
   * @param playerId The player id of the player who caused the game to end
   * @param gameCompletionType The reason why the game ended
   */
  private String getEndGameMessage(UUID playerId, GameCompletionType gameCompletionType) {
    var playerName = getPlayerName(playerId);

    return switch (gameCompletionType) {
      case PLAYER_COMMAND_SUCCESS -> playerName
          + " indicated the game is finished. Players will be redirected back to the lobby";
      case PLAYER_COMMAND_FAIL -> playerName
          + " indicated the game has finished unsuccessfully. Players will be redirected back to the lobby";
      case ABORT_MAX_TURN ->
          "The game has exceeded the maximum number of allowed turns. Game will be aborted and players will be redirected back to the lobby";
      case ABORT_TIME_OUT ->
          "The game has exceeded the maximum number of time allowed. Game will be aborted and players will be redirected back to the lobby";
      case ABORT_PLAYER_LEAVE -> "The game has ended because " + playerName
          + " has left the game. Players will be redirected back to the lobby";
    };
  }

  private void sendConfirmationCodeToPlayer(
      Player player,
      GameConfig gameConfig,
      PlayerGameConfig playerGameConfig
  ) {
    // The structure of the confirmation code structure depends on whether the challenge the game is
    // related to is a human or agent challenge.
    //
    // For Agent Challenges:
    //     {tournamentId}:{challenge type}:{challengeId}:{taskId}:{gameId}:{roleId}:{playerId}:{agentServiceId}
    //
    // For Human Challenges
    //     {tournamentId}:{challenge type}:{challengeId}:{taskId}:{gameId}:{roleId}:{playerId}

    // if the game itself is not associated with a specific challenge then the `challenge type` and
    // `challenge id` information won't be included in the confirmation code
    var confirmationCodeComponents = Stream.of(
        gameConfig.tournamentId,
        gameConfig.challengeType == null ? null : gameConfig.challengeType.toString(),
        gameConfig.challengeId,
        gameConfig.taskId,
        gameConfig.gameId,
        playerGameConfig.roleId
    );

    if (gameConfig.challengeType == ChallengeType.AGENT_CHALLENGE) {
      assert gameConfig.agentServiceIdsInGame != null :
          "agentServiceIdsInGame should not be null when we try to finish an agent challenge game";

      assert gameConfig.agentServiceIdsInGame.length == 1 :
          "agentServiceIdsInGame should only have one element when we try to finish an agent challenge game";

      confirmationCodeComponents = Stream.concat(
          confirmationCodeComponents,
          Stream.of(gameConfig.agentServiceIdsInGame[0])
      );
    }

    var confirmationCode = confirmationCodeComponents
        .filter(Objects::nonNull)
        .collect(Collectors.joining(":"));

    var gzipConfirmationCode = TextUtils.gzipCompressAndBase64Encode(confirmationCode);
    assert gzipConfirmationCode.length() <= 256 :
        "Maximum size for confirmation code is 256, but one with %d characters was generated".formatted(
            gzipConfirmationCode.length());

    player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Thank you for playing!");
    player.sendMessage(
        "By clicking the text below you'll get your confirmation code entered into your "
            + "chat box. From there you can copy it by selecting the text with Ctrl+A and then Ctrl+C to "
            + "copy to clipboard.");

    // players can't copy text from the chat log in Minecraft, so we can't just send
    // them the confirmation code as a normal text message. `tellraw` is a
    // vanilla MC command that, among other things, allows us to pre-fill the
    // text-box of the user with some text when the user clicks a message. We
    // use this workaround to get the confirmation code in the user's text-box,
    // from where they are able to copy it For more information on the `tellraw`
    // command check out:
    // https://minecraft.wiki/w/Commands/tellraw

    ServerUtils.tellRawToPlayer(player.getName(), Map.of(
        "text", "\n>> Click here to get your confirmation code!\n",
        "bold", true,
        "color", "gold",
        "clickEvent", Map.of(
            "action", "suggest_command",
            "value", gzipConfirmationCode)));
  }

  public void endTurn(PlayerGameConfig playerGameConfig, TurnChangeReason turnChangeReason) {
    assert playerGameConfig != null :
        "playerGameConfig should not be null when we try to finish a game";

    assert gameConfig != null :
        "gameConfig should not be null when we try to finish a game";

    if (turnChangeReason == TurnChangeReason.ABORT_TIME_OUT) {
      var playerInGameUUID = UUID.fromString(playerGameConfig.playerId);
      var turnEndMessage = getTurnEndMessage(playerInGameUUID, playerGameConfig, turnChangeReason);
      var playerInGame = PluginUtils.getPlayer(playerInGameUUID);
      if (playerInGame != null) {
        playerInGame.sendMessage(CommonConstants.CHAT_COLOR_INFO + turnEndMessage);
      }

      MinecraftLogger.info(turnEndMessage);

      var previousActiveRoleId = getCurrentPlayerRole().roleId;
      var nextActiveRoleId = getPlayerRoleAtOffset(1).roleId;

      var platformPlayerTurnChangeEvent = new PlatformPlayerTurnChangeEvent();
      platformPlayerTurnChangeEvent.setReason(TurnChangeReason.ABORT_TIME_OUT);
      platformPlayerTurnChangeEvent.setPreviousActiveRoleId(previousActiveRoleId);
      platformPlayerTurnChangeEvent.setNextActiveRoleId(nextActiveRoleId);

      EventHubProducerClient.sendGameEvent(
          platformPlayerTurnChangeEvent,
          gameConfig,
          playerGameConfig);
    }

    takeTurn();
  }

  private String getPlayerName(UUID playerId) {
    // if the player is a bot then get the name from it's AgentBot instance, otherwise just get it
    // from the bukkit Player instance
    var optionalAgentBot = AgentManager.getAgentByKey(playerId);

    var playerName = "";
    if (optionalAgentBot.isPresent()) {
      var agentBot = optionalAgentBot.get();
      playerName = agentBot.getName();
    } else {
      var player = PluginUtils.getPlayer(playerId);
      playerName = player.getName();
    }

    return playerName;
  }

  private String getTurnEndMessage(
      UUID playerId,
      PlayerGameConfig playerGameConfig,
      TurnChangeReason turnChangeReason
  ) {
    var playerName = getPlayerName(playerId);

    switch (turnChangeReason) {
      case ABORT_TIME_OUT: {
        return "Player " + playerName + "'s turn as role " + playerGameConfig.roleName
            + " has taken longer than the maximum allowed time of "
            + playerGameConfig.maxTurnTimeSeconds / 60
            + " minutes and will be ended. Game will continue to the next player.";
      }
    }

    return null;
  }
}
