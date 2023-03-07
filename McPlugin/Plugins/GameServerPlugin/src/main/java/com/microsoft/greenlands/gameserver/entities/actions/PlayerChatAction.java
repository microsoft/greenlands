package com.microsoft.greenlands.gameserver.entities.actions;

import com.microsoft.greenlands.common.utils.MinecraftLogger;
import com.microsoft.greenlands.common.utils.PluginUtils;
import com.microsoft.greenlands.common.utils.ServerUtils;
import com.microsoft.greenlands.gameserver.utils.GameTrackingHelper;
import java.util.Map;
import java.util.UUID;

public class PlayerChatAction extends Action {

  protected String message = "";

  @Override
  public String toString() {
    return "Action[PlayerChat: " + message.substring(0, 20) + "]";
  }

  /**
   * @param agentKey Unique identifier for instance of Agent
   * @param message Message the NPC should send
   */
  public PlayerChatAction(UUID agentKey, String message) {
    super(agentKey);
    this.message = message;
  }

  @Override
  protected long getStateCheckIntervalTicks() {
    return 1;
  }

  @Override
  protected Boolean hasTimedOut() {
    return System.currentTimeMillis() > getStartTimeMs() + defaultTimeoutMs;
  }

  @Override
  public void setUp() {
    if (!npc.isSpawned()) {
      MinecraftLogger.info("NPC " + agentKey + " cannot simulate" + this.toString()
          + " because it has not been spawned");
      this.transitionToState(ActionState.FAILURE);
      return;
    }

    var gameConfig = GameTrackingHelper.getGameConfigForPlayer(agentKey);
    for (var playerId : gameConfig.playerIdsInGame) {
      var playerInGameUUID = UUID.fromString(playerId);
      var playerInGame = PluginUtils.getPlayer(playerInGameUUID);

      if (playerInGame != null) {
        ServerUtils.tellRawToPlayer(playerInGame.getName(), Map.of(
            "text", "<" + npc.getName() + "> " + message,
            "color", "white"));
      }
    }

    transitionToState(ActionState.SUCCESS);
  }

  @Override
  public void execute() {

  }

  public String getMessage() {
    return message;
  }
}
