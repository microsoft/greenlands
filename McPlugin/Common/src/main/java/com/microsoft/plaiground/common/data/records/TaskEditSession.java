package com.microsoft.plaiground.common.data.records;

import com.microsoft.plaiground.common.data.RedisRecord;
import com.microsoft.plaiground.common.data.annotations.RedisKey;

/**
 * This class represents the intent of an author to edit a task. A record of this kind is created on
 * the Lobby and then read from the Task Edit server.
 */
public class TaskEditSession implements RedisRecord {

  @RedisKey
  public String playerId;

  public String tournamentId;
  public String taskId;

  /**
   * Empty constructor required be Redis deserialization.
   */
  public TaskEditSession() {
  }

  /**
   * Constructs instance using only the required RedisKeys.
   */
  public TaskEditSession(String playerId) {
    this.playerId = playerId;
  }

  /**
   * Constructor for all properties.
   */
  public TaskEditSession(String playerId, String tournamentId, String taskId) {
    this.playerId = playerId;
    this.tournamentId = tournamentId;
    this.taskId = taskId;
  }
}
