package com.microsoft.plaiground.common.helpers;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.microsoft.plaiground.common.config.CommonApplicationConfig;
import com.microsoft.plaiground.common.config.CommonApplicationConfigProvider;
import com.microsoft.plaiground.common.providers.JedisClient;
import com.microsoft.plaiground.common.providers.JedisClientProvider;
import com.microsoft.plaiground.common.providers.PlaigroundServiceApi;
import com.microsoft.plaiground.common.providers.StorageClientProvider;
import com.microsoft.plaiground.common.utils.MinecraftLogger;
import com.microsoft.plaiground.common.utils.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import static com.google.inject.Guice.createInjector;

/**
 * Abstract class with common, necessary functionalities of Plugins. Implements the basic logic of
 * binding common dependencies, such as JedisClient, CommonApplicationConfig, Scheduler, etc.
 */
public abstract class PlaigroundPlugin extends JavaPlugin {

  public static class CommonDependencyContainer extends AbstractModule {

    @Override
    protected void configure() {
      try {
        bind(CommonApplicationConfig.class)
            .toConstructor(CommonApplicationConfigProvider.class.getConstructor());
        bind(JedisClient.class)
            .toConstructor(JedisClientProvider.class.getConstructor(CommonApplicationConfig.class));
        bindScheduler();
      } catch (NoSuchMethodException | SecurityException e) {
        e.printStackTrace();
      }
    }

    protected void bindScheduler() throws NoSuchMethodException, SecurityException {
      // Binding the BukkitScheduler will facilitate testing the Scheduler class.
      bind(BukkitScheduler.class).toInstance(Bukkit.getScheduler());
      bind(Scheduler.class).in(Scopes.SINGLETON);
    }
  }

  private final CommonDependencyContainer container = new CommonDependencyContainer();
  protected Injector injector;

  /**
   * Inject dependencies common to all plugins.
   */
  protected void loadDependencies() {
    injector = createInjector(container);
    // Some objects are not used by the Plugins directly, but they need to be instantiated
    // when the Plugin starts because other modules like WorldUtils may depend on them.
    // Eventually, everything should be refactored to eliminate those dependencies, and
    // maybe this class would become obsolete.
    // Additionally, GameServerPlugin does not take the common dependencies via the constructor.
    var appConfig = injector.getInstance(CommonApplicationConfig.class);

    MinecraftLogger.info(
        "Starting PlaigroundPlugin with environment: " + appConfig.environmentSettings()
            .executionEnvironment());

    // initialize clients
    injector.getInstance(JedisClient.class);
    PlaigroundServiceApi.initialize(appConfig);
    StorageClientProvider.initialize(appConfig);

    // initialize scheduler
    injector.getInstance(Scheduler.class);
  }

}
