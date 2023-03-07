package com.microsoft.greenlands.common.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Event listener that is shared across all plugins. Implements basic, shared,
 * listeners.
 */
public class CommonWorldListener implements Listener {

  @EventHandler()
  public void onPlayerJoin(PlayerJoinEvent playerJoinEvent) {
    // don't send message to other players informing them that this player
    // joined
    playerJoinEvent.joinMessage(null);
  }

  @EventHandler()
  public void onPlayerQuit(PlayerQuitEvent playerQuitEvent) {
    // don't send message to other players informing them that this player
    // exited the server
    playerQuitEvent.quitMessage(null);
  }

  @EventHandler()
  public void onPlayerAnimation(PlayerKickEvent playerKickEvent) {
    // don't send message to other players informing them that this player
    // was kicked from the server
    playerKickEvent.leaveMessage(null);
  }
}
