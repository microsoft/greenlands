package com.microsoft.greenlands.lobbyserver.listeners;

import com.microsoft.greenlands.common.constants.CommonConstants;
import com.microsoft.greenlands.common.utils.MetadataUtils;
import com.microsoft.greenlands.common.utils.MinecraftLogger;
import com.microsoft.greenlands.common.utils.ServerUtils;
import com.microsoft.greenlands.lobbyserver.enums.LobbyMetadataKeys;
import java.util.Map;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Event listener that only listens to events originated in the lobby world.
 */
public class LobbyWorldListener implements Listener {

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerChatEvent(io.papermc.paper.event.player.AsyncChatEvent chatEvent) {
    chatEvent.viewers().clear();
    chatEvent.setCancelled(true);

    var player = chatEvent.getPlayer();
    player.sendMessage(CommonConstants.CHAT_COLOR_WARNING
        + "You may not chat in the lobby. You may only enter commands.");
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerJoinEvent(PlayerJoinEvent eventIn) {
    var playerName = eventIn.getPlayer().getName();

    ServerUtils.tellRawToPlayer(playerName, Map.of(
        "text", "\nMicrosoft Privacy Statement: ",
        "color", "white"));

    ServerUtils.tellRawToPlayer(playerName, Map.of(
        "text", "https://go.microsoft.com/fwlink/?LinkId=521839\n",
        "bold", true,
        "color", "blue",
        "clickEvent", Map.of(
            "action", "open_url",
            "value", "https://go.microsoft.com/fwlink/?LinkId=521839"
        )));
  }

  /**
   * When a player damages a block in the lobby this event is fired. There are special blocks in the
   * lobby that are meant to be hit by the player to select what kind of "role" the player wants to
   * play. This method checks if one such block was hit and adds the player to the queue for that
   * role.
   */
  @EventHandler
  public void checkSelectedGameType(PlayerAnimationEvent event) {
    if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
      var block = event.getPlayer().getTargetBlock(3);

      if (MetadataUtils.entityHasMetadata(block, LobbyMetadataKeys.SIGN_NAME)) {
        var player = event.getPlayer();

        var selectedAction = MetadataUtils.getEntityMetadataAsString(block,
            LobbyMetadataKeys.SIGN_NAME);

        // if player already has metadata then do nothing (prevent from registering multiple times)
        var currentSelectedAction = MetadataUtils.getEntityMetadataAsString(player,
            LobbyMetadataKeys.TASK_CHOICE);
        if (currentSelectedAction != null) {
          if (currentSelectedAction.equals(selectedAction)) {
            // don't register multiple times
            return;
          } else {
            // remove previous registration and allow task change
            player.sendMessage("Changing selected role...");
          }
        }

        MinecraftLogger.info(
            "Player %s has selected to be a %s".formatted(player.getName(), selectedAction));
        MetadataUtils.setEntityMetadata(player, LobbyMetadataKeys.TASK_CHOICE, selectedAction);
      }
    }
  }
}
