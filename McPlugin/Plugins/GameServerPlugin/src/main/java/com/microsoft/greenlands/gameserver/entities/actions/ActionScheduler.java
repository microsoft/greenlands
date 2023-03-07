package com.microsoft.greenlands.gameserver.entities.actions;

import com.google.inject.Inject;
import com.microsoft.greenlands.common.utils.MinecraftLogger;
import com.microsoft.greenlands.common.utils.Scheduler;
import javax.annotation.Nonnull;

public class ActionScheduler {

  private final Scheduler scheduler;
  private static ActionScheduler _instance;

  @Inject
  public ActionScheduler(Scheduler serverScheduler) {
    this.scheduler = serverScheduler;
    _instance = this;
  }

  /**
   * Gets the singleton instance of {@link ActionScheduler}. If ActionScheduler hasn't yet been
   * instantiated then this with throw an {@link AssertionError};
   */
  public static @Nonnull ActionScheduler getInstance() {
    assert _instance != null :
        "Tried to get Scheduler instance but it is not set. "
            + "Has it been instantiated on plugin onEnable?";

    return _instance;
  }

  /**
   * Private wrapper to schedule.
   */
  private static class ExecuteActionTask implements Runnable {

    private final Action action;
    private ActionCallback callback;

    public ExecuteActionTask(Action action, ActionCallback callback) {
      this.action = action;
      this.callback = callback;
    }

    /**
     * Schedules task to run every {@link Action#getStateCheckIntervalTicks}. This method should
     * cancel itself when a desired state is reached.
     * TODO: stop action if it's taking too long.
     */
    public void run() {
      action.execute();
      if (action.getState().hasFinished()) {
        ActionScheduler.getInstance().scheduler.cancelTask(action.getScheduledTaskId());
        // Notify action has ended
        callback.onActionEnd(action);
      } else if (action.hasTimedOut()) {
        MinecraftLogger.info("Action " + action.toString() + " has timed out!");
        action.transitionToState(Action.ActionState.FAILURE);
        ActionScheduler.getInstance().scheduler.cancelTask(action.getScheduledTaskId());
        // Notify action has ended
        callback.onActionTimeout(action);
      }
    }
  }

  /**
   * Schedules action for execution.
   *
   * @param action the action to schedule.
   * @param callback callback to call when event happens, for example, task completion.
   */
  public void schedule(Action action, ActionCallback callback) {
    action.transitionToState(Action.ActionState.RUNNING);
    action.setUp();
    // If Action is not finished after setUp, schedule periodic checks
    if (!action.getState().hasFinished()) {
      var executeActionTask = new ActionScheduler.ExecuteActionTask(action, callback);
      // Schedule action waiting only 1 tick before start
      var executeActionTaskId = scheduler
          .scheduleRepeatingTaskByTicks(executeActionTask, 1, action.getStateCheckIntervalTicks());
      action.hasBeenScheduled(executeActionTaskId);
    } else {
      callback.onActionEnd(action);
    }
  }

  /**
   * Cancels the task that executes action, if it has been scheduled. Has no effect otherwise.
   *
   * @param action Action to cancel.
   */
  public void cancelAction(Action action) {
    var taskId = action.getScheduledTaskId();
    if (taskId != null) {
      scheduler.cancelTask(taskId.intValue());
    }
  }
}
