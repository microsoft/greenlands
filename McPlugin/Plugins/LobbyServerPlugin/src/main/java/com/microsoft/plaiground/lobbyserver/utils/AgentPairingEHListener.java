package com.microsoft.plaiground.lobbyserver.utils;

import com.azure.messaging.eventhubs.models.PartitionEvent;
import com.microsoft.plaiground.client.model.AgentIsReadyEvent;
import com.microsoft.plaiground.client.model.EventSource;
import com.microsoft.plaiground.common.config.CommonApplicationConfig;
import com.microsoft.plaiground.common.providers.EventHubConsumerClientComponent;
import com.microsoft.plaiground.common.providers.PlaigroundServiceApi;
import com.microsoft.plaiground.common.utils.AsyncHelper;
import com.microsoft.plaiground.common.utils.EventConverter;
import com.microsoft.plaiground.common.utils.MinecraftLogger;
import com.microsoft.plaiground.lobbyserver.utils.pairing.AgentPairingSystem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AgentPairingEHListener {

  private static EventHubConsumerClientComponent partialConsumer;

  private static EventConverter eventConverter;

  /**
   * Once registered, this game loop will listen for "pairing requests" from agents. When it
   * receives one it will register the agent who sent it in the {@link AgentPairingSystem} so that
   * players can then play with it.
   */
  public static void registerLoop(CommonApplicationConfig appConfig) {
    if (partialConsumer != null) {
      // avoid initializing more than once
      return;
    }

    eventConverter = new EventConverter(PlaigroundServiceApi.getApiClient().getObjectMapper());

    partialConsumer = new EventHubConsumerClientComponent(
        appConfig.eventHubSettings().connectionString(),
        appConfig.eventHubSettings().name(),
        appConfig.eventHubSettings().consumerGroupLobbyServer(),
        AgentPairingEHListener::processEvent,
        error -> MinecraftLogger.severe(error.toString())
    );
  }

  /**
   * This callback is executed (on a thread which is not the main thread) for every event
   */
  private static void processEvent(PartitionEvent event) {
    var eventData = event.getData();

    // ignore events that don't come from an agent
    var eventSource = (String) eventData.getProperties().get("source");
    if (eventSource == null || !eventSource.equalsIgnoreCase(EventSource.AGENTSERVICE.toString())) {
      return;
    }

    try {
      var incomingEvent = eventConverter.threadSafeConvertEventDataToBaseEvent(eventData);

      // we only care about agent ready events
      if (incomingEvent instanceof AgentIsReadyEvent agentReadyEvent) {
        // check that the agent that wants to be paired actually exists
        var agent = PlaigroundServiceApi
            .agentsApi()
            .findAgentServiceById(agentReadyEvent.getAgentServiceId());

        if (agent == null) {
          MinecraftLogger.warning(
              "Received a request to register a new agent instance to the agent pairing queue "
                  + "but that agent doesn't exist in Service! "
                  + "Supposed agent's ID is: " + agentReadyEvent.getAgentServiceId()
          );
        } else {
          AsyncHelper.runOnMainThread(
              () -> AgentPairingSystem.registerAgent(agent, agentReadyEvent)
          );
        }
      }

    } catch (IOException e) {
      MinecraftLogger.severe("Failed to parse event body into BaseEvent. Received event body: "
          + new String(event.getData().getBody(), StandardCharsets.UTF_8));

    } catch (ClassNotFoundException e) {
      MinecraftLogger.severe("The incoming event type is unknown: "
          + new String(event.getData().getBody(), StandardCharsets.UTF_8));
    }
  }
}
