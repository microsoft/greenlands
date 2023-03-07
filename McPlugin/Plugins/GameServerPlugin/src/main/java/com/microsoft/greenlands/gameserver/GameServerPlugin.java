package com.microsoft.greenlands.gameserver;

import com.microsoft.greenlands.common.config.CommonApplicationConfig;
import com.microsoft.greenlands.common.helpers.GreenlandsPlugin;
import com.microsoft.greenlands.common.listeners.CommonWorldListener;
import com.microsoft.greenlands.common.providers.EventHubProducerClient;
import com.microsoft.greenlands.common.providers.JedisClient;
import com.microsoft.greenlands.common.providers.JedisClientProvider;
import com.microsoft.greenlands.common.utils.AsyncHelper;
import com.microsoft.greenlands.common.utils.MinecraftLogger;
import com.microsoft.greenlands.common.utils.Scheduler;
import com.microsoft.greenlands.common.utils.ServerUtils;
import com.microsoft.greenlands.common.utils.WorldUtils;
import com.microsoft.greenlands.gameserver.commands.GameConsoleCommands;
import com.microsoft.greenlands.gameserver.entities.AgentBot;
import com.microsoft.greenlands.gameserver.entities.actions.ActionScheduler;
import com.microsoft.greenlands.gameserver.listeners.GameListener;
import com.microsoft.greenlands.gameserver.listeners.GameWorldListener;
import com.microsoft.greenlands.gameserver.utils.AgentActionRequestEHListener;
import com.microsoft.greenlands.gameserver.utils.GameTrackingHelper;
import org.bukkit.Bukkit;

public class GameServerPlugin extends GreenlandsPlugin {

  private static GameServerPlugin _instance;

  public GameServerPlugin() {
    _instance = this;
  }

  public static GameServerPlugin getInstance() {
    return _instance;
  }

  @Override
  protected void loadDependencies() {
    super.loadDependencies();

    injector.getInstance(ActionScheduler.class);
    injector.getInstance(JedisClient.class);

    var appConfig = injector.getInstance(CommonApplicationConfig.class);

    AsyncHelper.registerLoop();
    GameTrackingHelper.initialize(appConfig);
    EventHubProducerClient.registerLoop(appConfig);
    AgentActionRequestEHListener.registerLoop(appConfig);

    Scheduler.getInstance().scheduleRepeatingTaskByMs(() -> {
      var activeGames = GameTrackingHelper.getAllActiveGames();
      activeGames.forEach((activeGame) -> {
        activeGame.endGameIfOverMaxTime();
        activeGame.advanceTurnIfOverMaxTime();
      });
    }, Scheduler.SECOND_MS);
  }

  @Override
  public void onEnable() {
    saveDefaultConfig();

    loadDependencies();

    // register listeners
    var pluginManager = Bukkit.getPluginManager();
    pluginManager.registerEvents(new CommonWorldListener(), this);
    pluginManager.registerEvents(new GameWorldListener(), this);
    pluginManager.registerEvents(new GameListener(), this);

    // setup communication with BungeeCord
    var pluginMessenger = this.getServer().getMessenger();
    pluginMessenger.registerOutgoingPluginChannel(this, "BungeeCord");

    // create worlds if necessary
    if (!WorldUtils.worldExists(WorldUtils.TEST_WORLD_NAME)) {
      WorldUtils.createTestWorld();
      ServerUtils.dispatchCommand("mv conf firstspawnoverride true");
      // set default world for server
      ServerUtils.dispatchCommand(
          "mv conf firstspawnworld %s".formatted(WorldUtils.TEST_WORLD_NAME));
    }

    // register console commands
    var appConfig = injector.getInstance(CommonApplicationConfig.class);
    GameConsoleCommands.registerCommands(appConfig);

    MinecraftLogger.info("Done loading plugin!");
  }

  @Override
  public void onDisable() {
    JedisClientProvider.getInstance().closePool();

    AgentBot.deregisterAllBots();
  }

}
