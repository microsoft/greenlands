package com.microsoft.plaiground.gameserver.entities.actions;

import com.microsoft.plaiground.gameserver.utils.AgentManager;
import java.util.UUID;
import net.citizensnpcs.api.npc.NPC;

/**
 * Represents processes to be scheduled and periodically checked for completion. The Action
 * behaviour is specified by overwriting methods {@link #setUp}, {@link #getStateCheckIntervalTicks}
 * and {@link #execute}.
 * </p>
 * Use {@link ActionScheduler} to correctly schedule action with callback. When scheduling an
 * Action, the following steps will be executed:
 * <ul>
 *     <li> Run method {@link #setUp}.
 *     <li> Schedule a repetitive that calls {@link #execute}
 *        every {@link #getStateCheckIntervalTicks}.
 *     <li> If action.state.{@link ActionState#hasFinished()} returns true, calls the
 *        {@link ActionCallback#onActionEnd} method.
 * </ul>
 * </p>
 */
public abstract class Action {

  public final UUID agentKey;
  public final NPC npc;
  private ActionState state;
  // How many ticks to wait before checking STATE and canceling task if necessary.
  protected static final long stateCheckIntervalTicks = 100;
  // Default milliseconds to wait before action times out.
  protected static final long defaultTimeoutMs = 2000;
  // Id of scheduled task, if the action is running. Otherwise is null.
  private Integer scheduledTaskId;
  // Server tick in which action was started.
  private long startTimeMs = 0;

  /**
   * Creates a new action to be performed by a Citizen NPC.
   *
   * @param agentKey Unique identifier for instance of Agent
   */
  public Action(UUID agentKey) {
    this.agentKey = agentKey;
    var agentBotOptional = AgentManager.getAgentByKey(agentKey);

    assert agentBotOptional.isPresent() :
        "Tried to create new action for bot " + agentKey + " but it is not present in the "
            + "agent manager";

    this.npc = agentBotOptional.get().getNpc();
    this.state = ActionState.READY;
    this.scheduledTaskId = null;
  }

  /**
   * Ensures correct transition between possible ActionStates
   *
   * <pre>
   * (1) READY: Action is created but has not been run yet.
   * (2) RUNNING: Action is incomplete. Call execute until finished.
   * (3) SUCCESS or FAILURE: task has been completed with corresponding status.
   *     No further calls are necessary.
   * </pre>
   *
   * Possible transitions are 1 -> 2 -> 3. Any other will result in IllegalStateException.
   */
  public enum ActionState {
    EVENT_PRODUCED {
      @Override
      public Boolean isValidTransition(ActionState nextState) {
        // Event Produced is a terminal state. It is not allowed to transition to any other states from terminal states.
        return false;
      }
    }, SUCCESS {
      @Override
      public Boolean isValidTransition(ActionState nextState) {
        // produce event to event hub after task has ended on success
        return true;
      }
    }, FAILURE {
      @Override
      public Boolean isValidTransition(ActionState nextState) {
        // Action Failure is a terminal state. It is not allowed to transition to any other states from terminal states.
        return false;
      }
    }, RUNNING {
      @Override
      public Boolean isValidTransition(ActionState nextState) {
        return nextState == SUCCESS || nextState == FAILURE;
      }
    }, READY {
      @Override
      public Boolean isValidTransition(ActionState nextState) {
        return nextState == RUNNING;
      }
    };

    public abstract Boolean isValidTransition(ActionState nextState);

    public boolean hasFinished() {
      return this == ActionState.FAILURE
          || this == ActionState.SUCCESS;
    }
  }

  protected void transitionToState(ActionState nextState) {
    if (state.isValidTransition(nextState)) {
      state = nextState;
    } else {
      throw new IllegalStateException("Illegal ActionState transition from " + state
          + " to " + nextState);
    }
  }

  public ActionState getState() {
    return state;
  }

  @Override
  public String toString() {
    return "Action[Base, State check tick interval: " + ((Long) stateCheckIntervalTicks).toString()
        + "]";
  }

  /**
   * Marks the Action as scheduled, saving scheduled taskId and start time.
   *
   * @param taskId id of the task scheduled to call the {@link #execute} method periodically.
   */
  public void hasBeenScheduled(int taskId) {
    scheduledTaskId = (Integer) taskId;
    startTimeMs = System.currentTimeMillis();
  }

  /**
   * If action has been scheduled, return the id of the scheduled task.
   *
   * @return Id of scheduled task or null if action has not been scheduled.
   */
  public Integer getScheduledTaskId() {
    return scheduledTaskId;
  }

  /**
   * If action has been scheduled, return the start time in milliseconds.
   *
   * @return Start time of scheduled task or null if action has not been scheduled.
   */
  public long getStartTimeMs() {
    return startTimeMs;
  }

  /**
   * Returns the number of ticks to wait between calls to {@link #execute()}. MUST be overwritten by
   * sub-classes to return the updated value of attribute {@link stateCheckIntervalTicks}.
   */
  protected abstract long getStateCheckIntervalTicks();

  /**
   * Returns milliseconds to wait since {@link #getStartTimeMs()} before canceling action. MUST be
   * overwritten by sub-classes to return the updated value of attribute {@link defaultTimeoutMs}.
   */
  protected abstract Boolean hasTimedOut();

  /**
   * Execute initial operations before scheduling. When no further action is needed, call
   * {@link #transitionToState(ActionState.SUCCESS)} or
   * {@link #transitionToState(ActionState.FAILURE)} from within this method.
   */
  protected abstract void setUp();

  /**
   * Runs core of the action. When action is scheduled, this method should be called every
   * {@link #getStateCheckIntervalTicks()}. To cancel scheduled task, call
   * {@link #transitionToState(ActionState.SUCCESS)} or
   * {@link #transitionToState(ActionState.FAILURE)} from within this method.
   */
  public abstract void execute();

}
