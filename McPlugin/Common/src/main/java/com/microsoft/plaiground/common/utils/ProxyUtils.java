package com.microsoft.plaiground.common.utils;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageRecipient;
import javax.annotation.Nonnull;

public class ProxyUtils {

  public static final String LOBBY_SERVER_NAME = "LobbyServer";
  public static final String GAME_SERVER_BASE_NAME = "GameServer";

  private static void sendCommandToProxy(@Nonnull String command, String arg,
      PluginMessageRecipient customSender) {
    sendCommandToProxy(command, new String[]{arg}, customSender);
  }

  /**
   * Sends a "raw" command to the proxy server. See this page
   * https://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/ for a list of the
   * commands that "Waterfall/Bungeecord" accepts.
   */
  private static void sendCommandToProxy(@Nonnull String command, String[] args,
      PluginMessageRecipient customSender) {
    ByteArrayDataOutput out = ByteStreams.newDataOutput();
    out.writeUTF(command);

    if (args != null) {
      for (var arg : args) {
        out.writeUTF(arg);
      }
    }

    var pluginInstance = PluginUtils.getPluginInstance();
    var sender = customSender != null
        ? customSender
        : pluginInstance.getServer();

    sender.sendPluginMessage(
        pluginInstance,
        "BungeeCord",
        out.toByteArray()
    );
  }

  /**
   * Sends player to the server with the given name. This will cause an exception if there is no
   * server with the specified name.
   */
  public static void sendPlayerToServer(Player player, String serverName) {
    sendCommandToProxy("Connect", serverName, player);
  }

  /**
   * Send player to the lobby server.
   */
  public static void sendPlayerToLobby(Player player) {
    sendPlayerToServer(player, LOBBY_SERVER_NAME);
  }

  /**
   * Send player to a game server.
   */
  public static void sendPlayerToGameServer(Player player) {
    // TODO in the future we can load balance here to which game server we want to send the player
    //  to
    // ^ maybe consider adding the game ID and from there derive which server to send to?
    //   we want to have a pool of task servers, how do we send players in a round robin fashion?
    //   ^ idea: server_name = "TaskServer-" + num_servers % (hash game_id)
    sendPlayerToServer(player, GAME_SERVER_BASE_NAME + "-1");
  }

}
