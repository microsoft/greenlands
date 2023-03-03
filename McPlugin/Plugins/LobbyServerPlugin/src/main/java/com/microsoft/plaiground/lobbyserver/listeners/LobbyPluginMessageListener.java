package com.microsoft.plaiground.lobbyserver.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.microsoft.plaiground.common.utils.MinecraftLogger;
import javax.annotation.Nonnull;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class LobbyPluginMessageListener implements PluginMessageListener {

  private static LobbyPluginMessageListener _instance;

  private LobbyPluginMessageListener() {
  }

  public static LobbyPluginMessageListener getInstance() {
    if (_instance == null) {
      _instance = new LobbyPluginMessageListener();
    }

    return _instance;
  }

  @Override
  public void onPluginMessageReceived(String channel, @Nonnull Player player, byte[] message) {
    if (!channel.equals("BungeeCord")) {
      return;
    }
    ByteArrayDataInput in = ByteStreams.newDataInput(message);
    String command = in.readUTF();

    // note we can branch on the command and read the arguments as required
    MinecraftLogger.info("Received message from BungeeCord, command: %s".formatted(command));
  }
}
