package com.microsoft.greenlands.gameserver.commands;

import com.microsoft.greenlands.common.constants.CommonConstants;
import com.microsoft.greenlands.common.utils.Scheduler;
import com.microsoft.greenlands.gameserver.entities.AgentBot;
import com.microsoft.greenlands.gameserver.utils.AgentManager;
import com.microsoft.greenlands.gameserver.utils.GameTrackingHelper;
import java.util.ArrayList;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;


class CreateRandomAgentCommand extends BukkitCommand {

  private static final String COMMAND = "create-dummy-agent";

  // Lists to keep track of created Agents.
  // TODO move this to Game Manager when we start to support games with bots.
  private static final ArrayList<Integer> runningTasks = new ArrayList<>();
  private static final ArrayList<AgentBot> runningBots = new ArrayList<>();

  public CreateRandomAgentCommand() {
    super(COMMAND);
    this.description = "Manages an Agent with (pseudo) random behaviour";
    this.usageMessage = "/" + COMMAND + " (create|destroyAll)";
    this.setAliases(new ArrayList<>());
  }

  @Override
  public boolean execute(@Nonnull CommandSender sender, @Nonnull String alias, String[] args) {
    var player = (Player) sender;

    if (args.length != 1) {
      sender.sendMessage("Must provide the action to perform (create|destroyAll)");
      return false;
    }

    var action = args[0];
    switch (action) {
      case "create":
        var gameConfig = GameTrackingHelper.getGameConfigForPlayer(player.getUniqueId());

        //! Warning: here we're using the player's ID as the agent key so that the agent manager is
        //! able to find a PlayerGameConfig. This could introduce weird behaviour during testing.
        var agentKey = player.getUniqueId();
        AgentManager.registerNewAgent(agentKey.toString(), gameConfig, null);

        var newAgent = AgentManager.getAgentByKey(agentKey).get();

        runningBots.add(newAgent);
        var taskId = Scheduler.getInstance().scheduleRepeatingTaskByMs(() -> {
          newAgent.enqueueAction("place_block_and_destroy");
        }, Scheduler.SECOND_MS, 20 * Scheduler.SECOND_MS);
        runningTasks.add(taskId);
        break;
      case "destroyAll":
        for (var runningAgent : runningBots) {
          runningAgent.destroy();
        }
        for (var runningTask : runningTasks) {
          Scheduler.getInstance().cancelTask(runningTask);
        }
        break;
      default:
        player.sendMessage(
            CommonConstants.CHAT_COLOR_ERROR + "Invalid option, use " + this.usageMessage);
        return false;
    }
    return true;
  }
}