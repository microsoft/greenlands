package com.microsoft.greenlands.common.utils;

import com.google.inject.Inject;
import com.microsoft.greenlands.common.constants.CommonConstants;
import javax.annotation.Nonnull;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * Utility class that allows to easily interface with the server's native Scheduler.
 */
public class Scheduler {

  public static final int MILLISECONDS_PER_TICK = 50;
  public static final int MILLISECOND = 1;
  public static final int SECOND_MS = MILLISECOND * 1000;
  public static final int MINUTE_MS = 60 * SECOND_MS;
  public static final int HOUR_MS = 60 * MINUTE_MS;
  private final BukkitScheduler serverScheduler;
  private static Scheduler _instance;

  @Inject
  public Scheduler(BukkitScheduler serverScheduler) {
    this.serverScheduler = serverScheduler;
    _instance = this;
  }

  /**
  * Gets the singleton instance of {@link Scheduler}.
  * If Scheduler hasn't yet been instantiated then this with throw an {@link AssertionError};
  */
  public static @Nonnull Scheduler getInstance() {
    assert _instance != null :
        "Tried to get Scheduler instance but it is not set. "
        + "Has it been instantiated on plugin onEnable?";

    return _instance;
  }

  /**
   * Converts the given amount of milliseconds to server ticks.
   */
  public static long convertMillisToTicks(long milliseconds) {
    // 1s = 20ticks
    // 1000ms = 20ticks
    // 50ms = 1tick
    return milliseconds / MILLISECONDS_PER_TICK;
  }

  public void executeWhenWorldReady(
    String worldName,
    Runnable runnable
  ) {
    executeWhenWorldReady(worldName, runnable, 0);
  }

  /**
   * Given a world and runnable, repeatedly calls itself after delay {@link CommonConstants.DELAY_TO_MAKE_WORLD_CREATION_EFFECTIVE} until world is ready, then executes the runnable.
   * In order to prevent an infinite loop, we track the number of times we've recursively called this method until we reach {@link CommonConstants.WORLD_READY_EXEC_MAX_CALL_COUNT}.
   */
  public void executeWhenWorldReady(
    String worldName,
    Runnable runnable,
    int numOfExecutionAttempts) {
    if (numOfExecutionAttempts > CommonConstants.WORLD_READY_EXEC_MAX_CALL_COUNT) {
      throw new RuntimeException("When attempting execute runnable after world %s is ready the maximum call %s count was reached".formatted(worldName, CommonConstants.WORLD_READY_EXEC_MAX_CALL_COUNT));
    }

    scheduleOnceWithDelay(() -> {
      var world = WorldUtils.getWorldManager().getMVWorld(worldName);
      if (world != null) {
        runnable.run();
      } else {
        executeWhenWorldReady(worldName, runnable, numOfExecutionAttempts + 1);
      }
    }, CommonConstants.DELAY_TO_MAKE_WORLD_CREATION_EFFECTIVE);
  }

  public int scheduleOnceWithDelay(Runnable runnable, long milliseconds) {
    return serverScheduler.scheduleSyncDelayedTask(PluginUtils.getPluginInstance(), runnable,
        convertMillisToTicks(milliseconds));
  }

  /**
   * Schedules a runnable to be executed repeatedly in the main server thread until cancelled.
   *
   * @param runnable the {@link Runnable} to be executed
   * @param msDelayBeforeFirstExecution milliseconds server will wait before first execution
   * @param msDelayBetweenExecutions milliseconds server will wait between each execution
   * @return the taskId that can be used to cancel the task
   */
  public int scheduleRepeatingTaskByMs(Runnable runnable, long msDelayBeforeFirstExecution,
      long msDelayBetweenExecutions) {
    return scheduleRepeatingTaskByTicks(runnable, convertMillisToTicks(msDelayBeforeFirstExecution),
        convertMillisToTicks(msDelayBetweenExecutions));
  }

  /**
   * Schedules a runnable to be executed repeatedly in the main server thread until cancelled.
   *
   * @param runnable the {@link Runnable} to be executed
   * @param msDelayBetweenExecutions milliseconds server will wait between each execution
   * @return the taskId that can be used to cancel the task
   */
  public int scheduleRepeatingTaskByMs(Runnable runnable, long msDelayBetweenExecutions) {
    return scheduleRepeatingTaskByMs(runnable, msDelayBetweenExecutions, msDelayBetweenExecutions);
  }

  /**
   * Schedules a runnable to be executed repeatedly in the main server thread until cancelled.
   *
   * @param runnable the {@link Runnable} to be executed
   * @param ticksDelayBeforeFirstExecution ticks server will wait before first execution
   * @param ticksDelayBetweenExecutions ticks server will wait between each execution
   * @return the taskId that can be used to cancel the task
   */
  public int scheduleRepeatingTaskByTicks(Runnable runnable,
      long ticksDelayBeforeFirstExecution, long ticksDelayBetweenExecutions) {
    return serverScheduler
        .scheduleSyncRepeatingTask(PluginUtils.getPluginInstance(), runnable,
            ticksDelayBeforeFirstExecution, ticksDelayBetweenExecutions);
  }

  /**
   * Schedules a runnable to be executed repeatedly in the main server thread until cancelled.
   *
   * @param runnable the {@link Runnable} to be executed
   * @param ticksDelayBetweenExecutions ticks server will wait between each execution
   * @return the taskId that can be used to cancel the task
   */
  public int scheduleRepeatingTaskByTicks(Runnable runnable,
      long ticksDelayBetweenExecutions) {
    return scheduleRepeatingTaskByTicks(runnable, ticksDelayBetweenExecutions,
        ticksDelayBetweenExecutions);
  }

  public void cancelTask(int taskId) {
    serverScheduler.cancelTask(taskId);
  }
}
