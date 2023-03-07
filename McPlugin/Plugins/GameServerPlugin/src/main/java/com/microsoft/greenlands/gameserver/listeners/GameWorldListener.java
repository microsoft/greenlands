package com.microsoft.greenlands.gameserver.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * The listeners in this class implement common behaviours we want to see in all our games. For
 * example, prevent players from breaking blocks with Y==0.
 */
public class GameWorldListener implements Listener {

  /**
   * Prevents players from breaking blocks they shouldn't
   */
  @EventHandler(priority = EventPriority.HIGH)
  public void preventPlayerFromBreakingIllegalBlocks(BlockBreakEvent event) {
    var block = event.getBlock();

    // don't allow players to break 0-level blocks
    if (block.getLocation().getBlockY() == 0) {
      event.setCancelled(true);
      return;
    }
  }
}
