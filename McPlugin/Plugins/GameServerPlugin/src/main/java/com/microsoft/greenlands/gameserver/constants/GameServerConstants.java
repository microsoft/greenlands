package com.microsoft.greenlands.gameserver.constants;

import com.microsoft.greenlands.common.utils.Scheduler;

public class GameServerConstants {

  public static final int DELAY_BEFORE_DELETE_WORLD_AFTER_GAME_FINISH = 10 * Scheduler.SECOND_MS;
  public static final int DELAY_BEFORE_SET_PLAYER_PROPERTIES_IN_GAME = 2 * Scheduler.SECOND_MS;

  public static final int PLAYER_MOVE_EVENT_MINIMUM_YAW = 10;
  public static final int PLAYER_MOVE_EVENT_MINIMUM_PITCH = 10;
  public static final int PLAYER_MOVE_EVENT_MINIMUM_DISTANCE = 1;

  public static final int DELAY_BEFORE_GAME_SAVE_AFTER_GAME_END = 10 * Scheduler.SECOND_MS;

  public static final int MAX_INTERACTION_DISTANCE = 5;
}
