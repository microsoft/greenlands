package com.microsoft.plaiground.lobbyserver.commands;

import com.microsoft.plaiground.common.config.CommonApplicationConfig;
import com.microsoft.plaiground.common.constants.CommonConstants;
import com.microsoft.plaiground.common.utils.PluginUtils;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;

/**
 * This type is in charge of handling all the commands that users send through Minecraft.
 */
public class LobbyConsoleCommands {

  public static void registerCommands(CommonApplicationConfig config) {
    var commandMap = PluginUtils.getPluginInstance().getServer().getCommandMap();

    var commandList = new ArrayList<Command>(List.of(
        new JoinTaskWithAgentCommand()));

    if (config.environmentSettings().isDevelopment()) {
      commandList.add(new TeleportTestWorldCommand());
    }

    commandMap.registerAll(
        CommonConstants.COMMON_COMMAND_PREFIX,
        commandList);
  }
}
