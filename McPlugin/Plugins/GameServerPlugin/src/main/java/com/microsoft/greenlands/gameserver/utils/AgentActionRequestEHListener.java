package com.microsoft.greenlands.gameserver.utils;

import com.azure.messaging.eventhubs.models.PartitionEvent;
import com.microsoft.greenlands.client.model.EventSource;
import com.microsoft.greenlands.client.model.PlatformGameEndEvent;
import com.microsoft.greenlands.common.config.CommonApplicationConfig;
import com.microsoft.greenlands.common.providers.EventHubConsumerClientComponent;
import com.microsoft.greenlands.common.providers.GreenlandsServiceApi;
import com.microsoft.greenlands.common.utils.AsyncHelper;
import com.microsoft.greenlands.common.utils.EventConverter;
import com.microsoft.greenlands.common.utils.MinecraftLogger;
import com.microsoft.greenlands.common.utils.Scheduler;
import com.microsoft.greenlands.gameserver.constants.GameServerConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * This client is in charge of listening to events that come from EventHub, finding the events that
 * were sent by agents (these are request for actions from agents), and then handing off these
 * events to the agent manager so that they can properly be handled.
 */
public class AgentActionRequestEHListener {

  private static EventHubConsumerClientComponent partialConsumer;

  private static EventConverter eventConverter;

  public static void registerLoop(CommonApplicationConfig appConfig) {
    if (partialConsumer != null) {
      // avoid initializing more than once
      return;
    }

    eventConverter = new EventConverter(GreenlandsServiceApi.getApiClient().getObjectMapper());

    partialConsumer = new EventHubConsumerClientComponent(
        appConfig.eventHubSettings().connectionString(),
        appConfig.eventHubSettings().name(),
        appConfig.eventHubSettings().consumerGroupGameServer(),
        AgentActionRequestEHListener::processEvent,
        error -> MinecraftLogger.severe(error.toString())
    );
  }

  /**
   * This callback is executed (on a thread which is not the main thread) for every event
   */
  private static void processEvent(PartitionEvent event) {
    var eventData = event.getData();

    // If we receive a PlatformGameEndEvent event, save the game
    var eventType = (String) eventData.getProperties().get("eventType");
    var gameEndEvent = new PlatformGameEndEvent();
    MinecraftLogger.finest("Received event: " + eventType);

    if (eventType.equalsIgnoreCase(gameEndEvent.getEventType())) {
      var gameId = (String) eventData.getProperties().get("gameId");
      var taskId = (String) eventData.getProperties().get("taskId");

      var tournamentId = (String) eventData.getProperties().get("tournamentId");

      MinecraftLogger.info("Requesting Game " + gameId + " to be saved");

      // Wait to ensure Stream Analytics has time to transfer last PlatformGameEvent into Cosmos
      // TODO: Make this process more robust than waiting, if we had events could poll until end event exists?
      AsyncHelper.runOnMainThread(() -> {
        Scheduler.getInstance().scheduleOnceWithDelay(() -> {
          var gamesApi = GreenlandsServiceApi.gamesApi();
          var gameBlobUri = gamesApi.saveGame(taskId, gameId, tournamentId);

          MinecraftLogger.info("Game " + gameId + " saved at " + gameBlobUri);

        }, GameServerConstants.DELAY_BEFORE_GAME_SAVE_AFTER_GAME_END);
      });
    }

    // ignore events that don't come from an agent
    var eventSource = (String) eventData.getProperties().get("source");
    if (eventSource == null || !eventSource.equalsIgnoreCase(EventSource.AGENTSERVICE.toString())) {
      return;
    }

    try {
      // TODO: ignore AgentReady events
      //  ^ it's like to be more efficient to do it once we have the event type in the EventData properties
      var incomingEvent = eventConverter.threadSafeConvertEventDataToBaseEvent(eventData);
      AsyncHelper.runOnMainThread(() -> AgentManager.routeActionRequestToAgent(incomingEvent));

    } catch (IOException e) {
      MinecraftLogger.severe("Failed to parse event body into BaseEvent. Received event body: "
          + new String(event.getData().getBody(), StandardCharsets.UTF_8));

    } catch (ClassNotFoundException e) {
      MinecraftLogger.severe("The incoming event type is unknown: "
          + new String(event.getData().getBody(), StandardCharsets.UTF_8));
    }
  }
}
