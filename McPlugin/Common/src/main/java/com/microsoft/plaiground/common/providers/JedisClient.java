//-----------------------------------------------------------------------------
// Copyright (c) Microsoft Corporation.  All rights reserved.
//-----------------------------------------------------------------------------

package com.microsoft.plaiground.common.providers;

/**
 * A provider of PlaiGround data stored in Redis.
 */
public interface JedisClient {

  /**
   * Closes Jedis client pool. This method should be called only when the
   * <code>{@link org.bukkit.plugin.java.JavaPlugin}</code> that uses it is being
   * shut down.
   */
  void closePool();
}
