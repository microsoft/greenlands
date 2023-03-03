package com.microsoft.plaiground.common.utils;

import java.util.logging.Logger;

/**
 * Simple wrapper of a standard logger that logs messages to the server log.
 */
public class MinecraftLogger {

  private static final Logger _logger = PluginUtils.getPluginInstance().getLogger();

  /**
   * Logs an INFO severity message on the Minecraft log stream.
   *
   * <p>This method is thread safe.</p>
   */
  public static synchronized void info(String msg) {
    _logger.info(msg);
  }

  /**
   * Logs an FINEST severity message on the Minecraft log stream.
   *
   * <p>This method is thread safe.</p>
   */
  public static synchronized void finest(String msg) {
    _logger.finest(msg);
  }

  /**
   * Logs an SEVERE severity message on the Minecraft log stream.
   *
   * <p>This method is thread safe.</p>
   */
  public static synchronized void severe(String msg) {
    _logger.severe(msg);
  }

  /**
   * Logs a WARNING severity message on the Minecraft log stream.
   *
   * <p>This method is thread safe.</p>
   */
  public static synchronized void warning(String msg) {
    _logger.warning(msg);
  }
}
