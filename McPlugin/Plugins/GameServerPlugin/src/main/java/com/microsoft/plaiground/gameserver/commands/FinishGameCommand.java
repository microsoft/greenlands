package com.microsoft.plaiground.gameserver.commands;

import com.microsoft.plaiground.client.model.GameCompletionType;
import com.microsoft.plaiground.common.constants.CommonConstants;
import com.microsoft.plaiground.gameserver.utils.GameTrackingHelper;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

class FinishGameCommand extends BukkitCommand {

  private static final String COMMAND = "finish-game";

  public FinishGameCommand() {
    super(COMMAND);
    this.description = "Finishes the current game if you're in a game. You must provide a value of "
        + "'success' or 'failure' to indicate whether the game was successfully completed or not.";
    this.usageMessage = "/%s success | fail".formatted(COMMAND);
    this.setAliases(new ArrayList<>());
  }

  @Override
  public boolean execute(@Nonnull CommandSender sender, @Nonnull String alias, String[] args) {
    var player = (Player) sender;
    var playerId = player.getUniqueId();

    var playerGameConfig = GameTrackingHelper.getPlayerGameConfig(playerId);

    if (!playerGameConfig.canEvaluate) {
      player.sendMessage(
          CommonConstants.CHAT_COLOR_ERROR + "You're not allowed to mark the game as finished");
      return false;
    }

    // Because the final agents for IGLU are expected to perform poorly and almost always "fail" the task
    // Collecting that measurement from the evaluating role (architect) is not as valuable.
    // To simplify the experience we removed the requirement to provide success or failure as an argument
    var gameCompletionType = GameCompletionType.PLAYER_COMMAND_SUCCESS;

    GameTrackingHelper
        .getActiveGameStateForPlayer(playerId)
        .endGameAndNotify(playerId, gameCompletionType);

    return true;
  }

}


