package com.microsoft.plaiground.gameserver.commands;

import com.microsoft.plaiground.client.model.PlatformPlayerTurnChangeEvent;
import com.microsoft.plaiground.client.model.TurnChangeReason;
import com.microsoft.plaiground.common.constants.CommonConstants;
import com.microsoft.plaiground.gameserver.utils.GameTrackingHelper;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

class PlayerTurnEndCommand extends BukkitCommand {

  private static final String COMMAND = "end-turn";

  public PlayerTurnEndCommand() {
    super(COMMAND);
    this.description = "Indicates the end of your turn";
    this.usageMessage = "/" + COMMAND;
    this.setAliases(new ArrayList<>());
  }

  // We should ignore this command if it is executed by a player while is not its turn
  @Override
  public boolean execute(@Nonnull CommandSender sender, @Nonnull String alias, String[] args) {
    var player = (Player) sender;
    var activeGameState = GameTrackingHelper.getActiveGameStateForPlayer(player.getUniqueId());
    var currentPlayerRole = activeGameState.getCurrentPlayerRole();

    if (!player.getUniqueId().equals(currentPlayerRole.playerId)) {
      player.sendMessage(CommonConstants.CHAT_COLOR_WARNING
          + "You attempted to end the current turn but you do not have permission because is not your turn. Command will be ignored.");
      return false;
    }

    var previousRoleId = currentPlayerRole.roleId;
    activeGameState.takeTurn();
    var nextRoleId = activeGameState.getCurrentPlayerRole().roleId;

    var platformPlayerTurnChangeEvent = new PlatformPlayerTurnChangeEvent();
    platformPlayerTurnChangeEvent.setReason(TurnChangeReason.PLAYER_COMMAND);
    platformPlayerTurnChangeEvent.setPreviousActiveRoleId(previousRoleId);
    platformPlayerTurnChangeEvent.setNextActiveRoleId(nextRoleId);

    GameTrackingHelper.sendEventForPlayerId(platformPlayerTurnChangeEvent, player.getUniqueId());

    return true;
  }
}
