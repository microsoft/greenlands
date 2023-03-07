package com.microsoft.greenlands.lobbyserver.commands;

import com.microsoft.greenlands.common.enums.ChallengeType;
import com.microsoft.greenlands.lobbyserver.entities.PlayerPairingInfo;
import com.microsoft.greenlands.lobbyserver.utils.pairing.AgentPairingSystem;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

class JoinTaskWithAgentCommand extends BukkitCommand {

  private static final String COMMAND = "join-task-with-agent";

  public JoinTaskWithAgentCommand() {
    super(COMMAND);
    this.description = "Attempts to create game with the specified agent";
    this.usageMessage = "/%s pairing-code".formatted(COMMAND);
    this.setAliases(new ArrayList<>());
  }

  @Override
  public boolean execute(@Nonnull CommandSender sender, @Nonnull String alias, String[] args) {
    var player = (Player) sender;
    if (args.length != 1) {
      player.sendMessage("Must provide a valid code to join a game");
      return false;
    }

    // An example code we could get as input:
    //    {tournament id}:{challenge id}:{task id}:{agent id}:{expiration timestamp}
    //
    // The {expiration timestamp} segment is optional, and will only be
    // interpreted if provided.
    var joinCode = args[0].toLowerCase();

    var codeComponents = joinCode.split(":");
    if (codeComponents.length != 4 && codeComponents.length != 5) {
      player.sendMessage("The provided code is not properly formatted");
      return false;
    }

    var tournamentId = codeComponents[0];
    var challengeId = codeComponents[1];
    var taskId = codeComponents[2];
    var agentId = codeComponents[3];

    // only check the expiration timestamp if we're provided one
    if (codeComponents.length == 5) {
      var expirationDateTimeMs = codeComponents[4];

      // expirationDateTimeMs is a Unix timestamp in seconds that indicates the
      // UTC date at which the code will stop being valid. If
      // expirationDateTimeMs is in the past, then the joinCode is invalid. An
      // example timestamp: 1667585808335
      try {
        var expirationDateTimeMsLong = Long.parseLong(expirationDateTimeMs);
        var currentTime = System.currentTimeMillis();

        if (currentTime > expirationDateTimeMsLong) {
          throw new NumberFormatException();
        }

      } catch (NumberFormatException e) {
        player.sendMessage("The provided code is not valid");
        return false;
      }
    }

    // Create a group id with the components of join code which are stable throughout the challenge
    // {tournament id}:{challenge type}:{challenge id}
    // 85DF336F-8834-4E6F-8B14-96F0EDA93C68:ac:1A3AD430-7F8F-4AD9-89FF-C1035B5D80F4
    // This must match the string constructed by the service
    // https://dev.azure.com/deeplearningeng/Greenlands/_git/Greenlands?path=/Service/Greenlands.Api/Services/AgentsChallengesService/AgentChallengesService.cs&version=GBmain&line=102&lineEnd=103&lineStartColumn=1&lineEndColumn=1&lineStyle=plain&_a=contents
    var groupId = String.join(":", List.of(
      tournamentId,
      ChallengeType.AGENT_CHALLENGE.toString(),
      challengeId
    )).toLowerCase();

    AgentPairingSystem.requestGameWithAgent(
        new PlayerPairingInfo(player.getUniqueId(), false, joinCode, groupId),
        agentId,
        tournamentId,
        challengeId,
        taskId);

    return true;
  }
}
