package com.microsoft.plaiground.gameserver.entities.actions;

import com.microsoft.plaiground.gameserver.utils.AgentManager;
import com.microsoft.plaiground.gameserver.utils.GameTrackingHelper;
import java.util.UUID;

public class EndTurnAction extends Action {

  /**
   * Creates a new action to be performed by a Citizen NPC.
   *
   * @param agentKey Unique identifier for instance of Agent
   */
  public EndTurnAction(UUID agentKey) {
    super(agentKey);
  }

  @Override
  public String toString() {
    return "Action[EndTurnAction]";
  }

  @Override
  protected long getStateCheckIntervalTicks() {
    return 0;
  }

  @Override
  protected Boolean hasTimedOut() {
    return false;
  }

  @Override
  protected void setUp() {
    var agentBot = AgentManager.getAgentByKey(agentKey).get();
    GameTrackingHelper.getActiveGameState(agentBot.getGameConfig().gameId).takeTurn();
    transitionToState(ActionState.SUCCESS);
  }

  @Override
  public void execute() {

  }
}
