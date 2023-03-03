package com.microsoft.plaiground.common.utils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class AgentUtils {

  /**
   * Internally, the key identifying an agent instance is a UUID built from the  roleId and the
   * gameId that that specific agent instance is playing.
   */
  public static UUID getAgentKey(String gameId, String roleId) {
    return UUID.nameUUIDFromBytes((gameId + roleId).getBytes(StandardCharsets.UTF_8));
  }
}
