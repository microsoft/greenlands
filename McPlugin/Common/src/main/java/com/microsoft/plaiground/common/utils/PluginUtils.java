package com.microsoft.plaiground.common.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class PluginUtils {

  public static Player getPlayer(UUID playerId) {
    var server = getPluginInstance().getServer();
    var player = server.getPlayer(playerId);
    if (player != null) {
      return player;
    }
    
    var onlinePlayers = server.getOnlinePlayers();
    for (var onlinePlayer : onlinePlayers) {
      if (onlinePlayer.getUniqueId().equals(playerId)) {
        return onlinePlayer;
      }
    }

    throw new RuntimeException("Player with id " + playerId + " was not found on server " + server.getName());
  }

  public static JavaPlugin getPluginInstance() {
    return JavaPlugin.getProvidingPlugin(PluginUtils.class);
  }
}
