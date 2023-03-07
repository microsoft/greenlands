package com.microsoft.greenlands.lobbyserver.commands;

import com.microsoft.greenlands.client.model.TournamentRole;
import com.microsoft.greenlands.client.model.TournamentRoleActions;
import com.microsoft.greenlands.client.model.TournamentRoleCapabilities;
import com.microsoft.greenlands.common.data.records.PlayerGameConfig;
import com.microsoft.greenlands.common.providers.JedisClientProvider;
import com.microsoft.greenlands.common.utils.ProxyUtils;
import com.microsoft.greenlands.common.utils.Scheduler;
import java.util.ArrayList;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;

/**
 * Teleport player to task server's TestWorld for debugging.
 */
class TeleportTestWorldCommand extends BukkitCommand {

  private static final String COMMAND = "teleport-to-test-world";

  /**
   * Creates new Command handler.
   */
  public TeleportTestWorldCommand() {
    super(COMMAND);
    this.description = "Sends player to test world on task server";
    this.usageMessage = "/" + COMMAND;
    this.setAliases(new ArrayList<>());
  }

  @Override
  public boolean execute(@Nonnull CommandSender sender, @Nonnull String alias, String[] args) {
    var player = (Player) sender;
    var roleName = "test";
    var dummyGameId = "gameId";

    // save to redis
    var jedis = JedisClientProvider.getInstance();
    jedis.saveRecordWithExpiration(
        new PlayerGameConfig(
            player.getUniqueId().toString(),
            dummyGameId,
            new TournamentRole() {{
              setId(UUID.randomUUID().toString());
              setName(roleName);
              setDescription("A description for test role");
              setActions(new TournamentRoleActions() {{
                setCanPlaceBlocks(true);
                setCanRemoveBlocks(true);
                setCanSendTextMessage(true);
                setCanEvaluate(true);
                setCanToggleFlight(true);
              }});
              setCapabilities(new TournamentRoleCapabilities() {{
                setCanBeSeenByOtherPlayers(true);
                setCanSeeTargetGameState(true);
              }});
            }},
            null
        ),
        4 * Scheduler.HOUR_MS
    );

    ProxyUtils.sendPlayerToGameServer(player);
    return true;
  }

}
