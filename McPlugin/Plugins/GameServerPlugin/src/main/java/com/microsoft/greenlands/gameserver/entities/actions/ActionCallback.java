package com.microsoft.greenlands.gameserver.entities.actions;

import com.microsoft.greenlands.client.model.BlockPlaceEvent;
import com.microsoft.greenlands.client.model.BlockRemoveEvent;
import com.microsoft.greenlands.client.model.PlatformPlayerTurnChangeEvent;
import com.microsoft.greenlands.client.model.PlayerChatEvent;
import com.microsoft.greenlands.client.model.PlayerMoveEvent;
import com.microsoft.greenlands.client.model.TurnChangeReason;
import com.microsoft.greenlands.common.providers.EventHubProducerClient;
import com.microsoft.greenlands.common.utils.BlockUtils;
import com.microsoft.greenlands.common.utils.LocationUtils;
import com.microsoft.greenlands.common.utils.MinecraftLogger;
import com.microsoft.greenlands.gameserver.utils.AgentManager;
import com.microsoft.greenlands.gameserver.utils.GameTrackingHelper;
import java.util.UUID;

public class ActionCallback {

  /**
   * Function to call when Action has ended.
   */

  // Note: If only one type of ActionCallback object shared for all actions,
  // could change this class's methods to static,
  // so this object doesn't need to be passed as an argument to method
  // "ActionScheduler.getInstance().schedule()"
  public void onActionEnd(Action action) {
    if (!action.getState().hasFinished()) {
      throw new IllegalStateException("onActionEnd has been called with unfinished action "
          + action.toString());
    }

    var agentBot = AgentManager.getAgentByKey(action.agentKey).get();
    var gameConfig = agentBot.getGameConfig();
    var agentGameConfig = agentBot.getAgentGameConfig();

    if (action.getState() == Action.ActionState.FAILURE) {
      MinecraftLogger.warning(action.toString() + " has failed!");

      if (action instanceof PlayerMoveAction) {
        var agentLocation = action.npc.getStoredLocation().clone();

        var playerMoveEvent = new PlayerMoveEvent();
        playerMoveEvent.newLocation(LocationUtils.convertToGreenlandsLocation(agentLocation));

        EventHubProducerClient.sendGameEvent(
            playerMoveEvent,
            gameConfig,
            agentGameConfig
        );
      }
    }

    if (action.getState() == Action.ActionState.SUCCESS) {
      if (action instanceof PlayerMoveAction) {
        var playerMoveEvent = new PlayerMoveEvent();
        var targetLocation = ((PlayerMoveAction) action).getTargetLocation();
        playerMoveEvent.newLocation(LocationUtils.convertToGreenlandsLocation(targetLocation));
        EventHubProducerClient.sendGameEvent(
            playerMoveEvent,
            gameConfig,
            agentGameConfig
        );
      } else if (action instanceof BlockBreakAction) {
        var blockRemoveEvent = new BlockRemoveEvent();
        var targetLocation = ((BlockBreakAction) action).getTargetLocation();
        blockRemoveEvent.location(LocationUtils.convertToGreenlandsLocation(targetLocation));
        EventHubProducerClient.sendGameEvent(
            blockRemoveEvent,
            gameConfig,
            agentGameConfig
        );
      } else if (action instanceof BlockPlaceAction) {
        var blockPlaceEvent = new BlockPlaceEvent();
        var targetLocation = ((BlockPlaceAction) action).getTargetLocation();
        blockPlaceEvent.location(LocationUtils.convertToGreenlandsLocation(targetLocation));
        blockPlaceEvent.material(
            BlockUtils.MATERIAL_IDS.get(((BlockPlaceAction) action).getMaterial()));
        EventHubProducerClient.sendGameEvent(
            blockPlaceEvent,
            gameConfig,
            agentGameConfig
        );
      } else if (action instanceof EndTurnAction) {
        var activeGameState = GameTrackingHelper.getActiveGameStateForPlayer(
            UUID.fromString(agentGameConfig.playerId));
        // The callback is invoked AFTER the EndTurnAction has reached terminal state
        // which means the turn has been taken. The nextPlayer is actually the
        // current player
        var previousRoleId = activeGameState.getPlayerRoleAtOffset(-1).roleId;
        var nextRoleId = activeGameState.getCurrentPlayerRole().roleId;

        var platformPlayerTurnChangeEvent = new PlatformPlayerTurnChangeEvent();
        platformPlayerTurnChangeEvent.setReason(TurnChangeReason.PLAYER_COMMAND);
        platformPlayerTurnChangeEvent.setPreviousActiveRoleId(previousRoleId);
        platformPlayerTurnChangeEvent.setNextActiveRoleId(nextRoleId);

        EventHubProducerClient.sendGameEvent(
            platformPlayerTurnChangeEvent,
            gameConfig,
            agentGameConfig
        );
      } else if (action instanceof PlayerChatAction) {
        var playerChatEvent = new PlayerChatEvent();
        playerChatEvent.message(((PlayerChatAction) action).getMessage());
        EventHubProducerClient.sendGameEvent(
            playerChatEvent,
            gameConfig,
            agentGameConfig
        );
      }

      action.transitionToState(Action.ActionState.EVENT_PRODUCED);
      MinecraftLogger.info(
          "Action " + action.toString() + " has produced event and finished successfully!");
    }
  }

  public void onActionTimeout(Action action) {
    MinecraftLogger.info("Action " + action.toString() + " has timed out!");
  }
}