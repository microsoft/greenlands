package com.microsoft.plaiground.common.providers;

import java.util.function.Consumer;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.messaging.eventhubs.models.PartitionEvent;
import com.microsoft.plaiground.common.config.CommonApplicationConfig;
import com.microsoft.plaiground.common.utils.MinecraftLogger;

/**
 * This client is in charge of listening to events that come from EventHub. In
 * the constructor it will accept callback functions for events and for errors.
 * Any event that was sent to any of the partitions is handed off to the event
 * callback.
 *
 * The listening part happens on a separate thread, as does the handing off of
 * these events to the callback function. This means that the callback function
 * needs to be careful to manually execute things on the main server thread if
 * it needs to.
 */
public class EventHubConsumerClientComponent {

  private static final String connectionStringTemplate = "Endpoint=%s;SharedAccessKeyName=%s;SharedAccessKey=%s";
  private EventHubConsumerAsyncClient consumerClient = null;

  public EventHubConsumerClientComponent(
      String connectionString,
      String name,
      String consumerGroup,
      Consumer<PartitionEvent> eventConsumer,
      Consumer<? super Throwable> errorConsumer) {
    assert consumerGroup != null && !consumerGroup.isEmpty() : "Consumer group cannot be empty!";

    consumerClient = new EventHubClientBuilder()
        .connectionString(connectionString, name)
        .consumerGroup(consumerGroup)
        .buildAsyncConsumerClient();

    // set up our generic listener for each partition ID
    consumerClient.getPartitionIds().subscribe(partitionId -> {
      consumerClient.receiveFromPartition(partitionId, EventPosition.latest())
          .subscribe(
              partitionEvent -> this.eventConsumerWrapper(partitionEvent, eventConsumer),
              error -> this.errorConsumerWrapper(error, errorConsumer));
    });
  }

  private void eventConsumerWrapper(
      PartitionEvent partitionEvent,
      Consumer<PartitionEvent> eventConsumer) {
    try {
      eventConsumer.accept(partitionEvent);
    } catch (Exception e) {
      MinecraftLogger.severe("EventHubConsumerClientComponent - Error in event consumer: " + e.toString());
      MinecraftLogger.severe(ExceptionUtils.getStackTrace(e));
    }
  }

  private void errorConsumerWrapper(
      Throwable error,
      Consumer<? super Throwable> errorConsumer) {
    try {
      errorConsumer.accept(error);
    } catch (Exception e) {
      MinecraftLogger.severe("EventHubConsumerClientComponent - Error in error consumer: " + e.toString());
      MinecraftLogger.severe(ExceptionUtils.getStackTrace(e));
    }
  }

}
