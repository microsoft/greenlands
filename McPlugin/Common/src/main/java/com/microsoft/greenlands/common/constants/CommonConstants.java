package com.microsoft.greenlands.common.constants;

import org.bukkit.ChatColor;

import com.microsoft.greenlands.common.utils.Scheduler;

public class CommonConstants {

  public static final String COMMON_COMMAND_PREFIX = "greenlands";

  public static final ChatColor CHAT_COLOR_INFO = ChatColor.AQUA;
  public static final ChatColor CHAT_COLOR_WARNING = ChatColor.YELLOW;
  public static final ChatColor CHAT_COLOR_ERROR = ChatColor.RED;

  // TODO this is empirical. Is there an optimal value? or dynamic way to get this
  public static final int DELAY_TO_MAKE_WORLD_CREATION_EFFECTIVE = 5 * Scheduler.SECOND_MS;
  public static final int WORLD_READY_EXEC_MAX_CALL_COUNT = 10;

  public static final int DELAY_EVENT_HUB_PUBLISH_LOOP = 100;

  // https://jd.papermc.io/paper/1.18/org/bukkit/Chunk.html#getBlock(int,int,int)
  public static final int WORLD_MIN_CHUNK_SIZE = 16;
}
