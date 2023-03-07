package com.microsoft.greenlands.common.providers;

import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.models.CreateBatchOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.greenlands.client.model.BaseEvent;
import com.microsoft.greenlands.client.model.EventSource;
import com.microsoft.greenlands.common.config.CommonApplicationConfig;
import com.microsoft.greenlands.common.constants.CommonConstants;
import com.microsoft.greenlands.common.data.records.GameConfig;
import com.microsoft.greenlands.common.data.records.PlayerGameConfig;
import com.microsoft.greenlands.common.utils.EventConverter;
import com.microsoft.greenlands.common.utils.MinecraftLogger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.annotation.Nullable;

/**
 * This client is in charge of receiving events from the main Minecraft server thread and queuing
 * them so they're sent from a different thread to prevent bogging down the main thread.
 *
 * <p>When this class is initialized, a thread will be started that constantly checks the {@link
 * #eventsToSendQueue}, and if there are any items present then it will get all of them, separate
 * them by game id, and send them to Event Hub ensuring that all events in one
 * {@link com.azure.messaging.eventhubs.EventDataBatch} have the same game id (partition key is
 * calculated from the gameId). This thread checks for input events to send every
 * {@link CommonConstants#DELAY_EVENT_HUB_PUBLISH_LOOP} milliseconds.</p>
 *
 * <p>Main thread is meant to call {@link #sendGameEvent(BaseEvent, GameConfig, PlayerGameConfig)},
 * which will simply add the provided event into the queue.</p>
 */
public class EventHubProducerClient {

  private static com.azure.messaging.eventhubs.EventHubProducerClient producerClient = null;

  private static final Queue<BaseEvent> eventsToSendQueue = new ConcurrentLinkedDeque<>();
  private static EventConverter eventConverter;

  /**
   * Initializes the resources used by this class and starts the thread that checks for input
   * items.
   * TODO move this to constructor once we have dependency injection in place
   */
  public static void registerLoop(CommonApplicationConfig appConfig) {
    if (producerClient != null) {
      // avoid initializing more than once
      return;
    }

    eventConverter = new EventConverter(GreenlandsServiceApi.getApiClient().getObjectMapper());

    var settings = appConfig.eventHubSettings();
    var consumerGroup = settings.consumerGroupGameServer();
    assert consumerGroup != null && !consumerGroup.isEmpty() :
        "Consumer group cannot be empty!";

    var inputQueueProcessThread = new Thread(() -> {
      producerClient = new EventHubClientBuilder()
          .connectionString(settings.connectionString(), settings.name())
          .consumerGroup(consumerGroup)
          .buildProducerClient();

      while (true) {
        try {
          processInputQueue();
          Thread.sleep(CommonConstants.DELAY_EVENT_HUB_PUBLISH_LOOP);
        } catch (Exception e) {
          /* Ignore exceptions at this level of the loop */
          e.printStackTrace();
        }
      }
    });

    inputQueueProcessThread.setName("eh-client-input-process-thread");
    inputQueueProcessThread.start();
  }

  /**
   * Checks if there are input events in {@link #eventsToSendQueue} and schedules them to be sent.
   * This method is meant to be called from the thread that checks for input items in the queue.
   */
  private static void processInputQueue() {
    // TODO the current approach to process the input is very rudimentary but should work fine with
    // a moderate amount of events being triggered. In the future we can consider replacing this
    // implementation with a map-reduce pattern or a pool of threads that process the input
    var eventToSchedule = eventsToSendQueue.poll();

    if (eventToSchedule == null) {
      return;
    }

    var gameIdToEvents = new HashMap<String, ArrayList<BaseEvent>>();

    // first separate all events by gameId
    while (eventToSchedule != null) {
      var gameId = eventToSchedule.getGameId();

      if (!gameIdToEvents.containsKey(gameId)) {
        gameIdToEvents.put(gameId, new ArrayList<>());
      }
      gameIdToEvents.get(gameId).add(eventToSchedule);

      eventToSchedule = eventsToSendQueue.poll();
    }

    // then send all events for each game separately
    for (var entry : gameIdToEvents.entrySet()) {
      sendBatchOfEvents(entry.getValue());
    }
  }

  /**
   * Given a list of events, create a batch from them and send it to event hub. It assumes that all
   * events in the list belong to the same Game.
   *
   * <p>If the provided events don't fit into one batch then they will be split into multiple
   * batches.</p>
   *
   * @throws IllegalArgumentException if there is an event in the list that exceeds the allowed
   * event size. The maximum size in bytes is defined in
   * {@link com.azure.messaging.eventhubs.implementation.ClientConstants#MAX_MESSAGE_LENGTH_BYTES}
   */
  private static void sendBatchOfEvents(List<BaseEvent> events) {
    if (events.isEmpty()) { // this should never happen
      return;
    }

    var batchOptions = new CreateBatchOptions();
    batchOptions.setPartitionKey(events.get(0).getGameId());

    var eventDataBatch = producerClient.createBatch(batchOptions);

    for (var event : events) {
      try {
        var eventData = eventConverter.convertBaseEventToEventData(event);

        // Try to add event to batch. If it doesn't work then it means that current batch is already
        // too big to receive the new event, so we send the current batch and start a new one
        if (!eventDataBatch.tryAdd(eventData)) {
          MinecraftLogger.warning("Batch reached max size!");
          MinecraftLogger.info("Sending batch of " + eventDataBatch.getCount() + " events to "
              + producerClient.getEventHubName() + " event hub " + Instant.now().toString());
          producerClient.send(eventDataBatch);
          eventDataBatch = producerClient.createBatch(batchOptions);

          // if we can't add the event to a brand-new batch then we throw an error, since there is
          // not much we can do about it
          if (!eventDataBatch.tryAdd(eventData)) {
            throw new IllegalArgumentException("Event is too large for an empty batch. Max size: "
                + eventDataBatch.getMaxSizeInBytes());
          }
        }
      } catch (JsonProcessingException e) {
        MinecraftLogger.severe("Failed to convert BaseEvent into json");
        e.printStackTrace();
      }
    }

    // send the last batch of remaining events
    if (eventDataBatch.getCount() > 0) {
      MinecraftLogger.info("Sending batch of " + eventDataBatch.getCount() + " events to "
          + producerClient.getEventHubName() + " event hub " + Instant.now().toString());
      producerClient.send(eventDataBatch);
    }
  }

  /**
   * Called by the main server thread to send an event. It will set all the _base_ properties of the
   * provided {@link BaseEvent} instance using the information contained in the provided
   * {@link GameConfig}.
   *
   * If a {@link PlayerGameConfig} is provided then player-specific information will also be added
   * to the base event instance.
   */
  public static void sendGameEvent(
      BaseEvent baseEvent,
      GameConfig gameConfig,
      @Nullable PlayerGameConfig playerGameConfig
  ) {
    // set common properties
    baseEvent.setSource(EventSource.MINECRAFTPLUGIN);
    baseEvent.setId(UUID.randomUUID().toString());
    baseEvent.setProducedAtDatetime(OffsetDateTime.now(ZoneOffset.UTC).toString());
    baseEvent.setGameId(gameConfig.gameId);
    baseEvent.setTaskId(gameConfig.taskId);
    baseEvent.setTournamentId(gameConfig.tournamentId);

    if (playerGameConfig != null) {
      baseEvent.setRoleId(playerGameConfig.roleId);
    }

    // set groupId if present
    if (gameConfig.groupId != null) {
      baseEvent.setGroupId(gameConfig.groupId);
    }

    // set the agent filter if there is an agent in the game
    if (gameConfig.agentServiceIdsInGame.length > 0) {
      if (gameConfig.agentServiceIdsInGame.length > 1) {
        // TODO: We're assuming that every game has AT MOST one agent
        MinecraftLogger.warning(
            "Game has more than one agent. Only the first one will be used to populate event's agent filter.");
      }

      var agentId = gameConfig.agentServiceIdsInGame[0];

      baseEvent.setAgentSubscriptionFilterValue(agentId);
    }

    eventsToSendQueue.add(baseEvent);
  }
}
