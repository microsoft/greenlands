package com.microsoft.greenlands.gameserver.entities.mocks;

import com.microsoft.greenlands.gameserver.entities.actions.Action;
import java.util.UUID;

/**
 * Mock action that executes 3 times every 10 ticks.
 */
public class CountActionMock extends Action {

  public final int maxTimesToExecute = 3;
  public int executionCounter;
  public long stateCheckIntervalTicks = 10;

  public CountActionMock() {
    super(UUID.fromString("45cc110b-a4ae-4dab-bd15-553c86d17227"));
    executionCounter = 0;
  }

  @Override
  protected long getStateCheckIntervalTicks() {
    return stateCheckIntervalTicks;
  }

  @Override
  public Boolean hasTimedOut() {
    return false;
  }

  @Override
  public void setUp() {
  }

  @Override
  public void execute() {
    executionCounter += 1;
    if (executionCounter == maxTimesToExecute) {
      transitionToState(ActionState.SUCCESS);
    }
  }
}