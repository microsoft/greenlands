package com.microsoft.plaiground.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.plaiground.common.providers.PlaigroundServiceApi;
import java.util.Map;
import org.bukkit.Bukkit;

public class ServerUtils {

  /**
   * Executes the tellraw command on the given player using the provided parameter map as the params
   * to the command.
   *
   * Example:
   *
   * <pre>
   * ServerUtils.tellRawToPlayer(playerName, Map.of(
   *           "text", "\nMicrosoft Privacy Statement: ",
   *           "color", "white"));
   * </pre>
   */
  public static void tellRawToPlayer(String playerName, Map<String, Object> tellRawParams) {
    var objectMapper = PlaigroundServiceApi
        .getApiClient()
        .getObjectMapper();

    try {
      ServerUtils.dispatchCommand(
          "tellraw " + playerName + " " + objectMapper.writeValueAsString(tellRawParams) + " "
      );
    } catch (JsonProcessingException e) {
      MinecraftLogger.severe(
          "Failed to serialize body for tellraw command"
      );

      e.printStackTrace();
    }
  }

  /**
   * Executes a command as if server admin were executing one from the MC server console.
   *
   * @param command the command to be executed and its arguments. Exactly the same as a server admin
   * would write it from the MC server console.
   */
  public static void dispatchCommand(String command) {
    var server = Bukkit.getServer();
    server.dispatchCommand(server.getConsoleSender(), command);
  }
}
