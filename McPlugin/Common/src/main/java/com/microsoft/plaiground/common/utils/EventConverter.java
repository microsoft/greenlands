package com.microsoft.greenlands.common.utils;

import com.azure.messaging.eventhubs.EventData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.greenlands.client.model.BaseEvent;
import java.io.IOException;

/**
 * This is a small utility that knows how to map instances of {@link EventData} to
 * {@link BaseEvent}, and vice-versa.
 */
public class EventConverter {

  private static final String EVENT_TYPE_FIELD = "eventType";
  private static String eventsPackageLocation;
  private final ObjectMapper objectMapper;

  public EventConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;

    // We only need to set eventsPackageLocation once, since it will never change
    // while the application is running
    if (eventsPackageLocation == null) {
      // i.e. com.microsoft.greenlands.client.model.BaseEvent
      var baseEventsCanonicalName = BaseEvent.class.getCanonicalName();
      eventsPackageLocation = baseEventsCanonicalName.substring(0,
          baseEventsCanonicalName.lastIndexOf("."));
    }
  }

  public synchronized BaseEvent threadSafeConvertEventDataToBaseEvent(EventData eventData)
      throws IOException, ClassNotFoundException {
    return this.convertEventDataToBaseEvent(eventData);
  }

  /**
   * Take an instance of {@link EventData} and converts it to the appropriate subclass of
   * {@link BaseEvent}. This is done by checking the 'eventType' property of the event data, and
   * deserializing the payload to the event with that class name.
   *
   * For example, if the incoming event data has eventType=BlockPlaceEvent then we will try to
   * deserialize it to an instance of {@link com.microsoft.greenlands.client.model.BlockPlaceEvent}
   */
  public BaseEvent convertEventDataToBaseEvent(EventData eventData)
      throws IOException, ClassNotFoundException {

    var eventTypeName = (String) eventData.getProperties().get(EVENT_TYPE_FIELD);
    assert eventTypeName != null :
        "Tried to convert EventData to BaseEvent but 'eventType' property was not set! Raw event: "
            + eventData.getBodyAsString();

    var targetEventClass = Class.forName(eventsPackageLocation + "." + eventTypeName);

    return (BaseEvent) objectMapper.readValue(eventData.getBody(), targetEventClass);
  }

  /**
   * Takes an instance of {@link BaseEvent} and converts it to the {@link EventData} which we can
   * then proceed to send to EventHub.
   */
  public EventData convertBaseEventToEventData(BaseEvent baseEvent)
      throws JsonProcessingException {
    String eventBody = null;
    eventBody = objectMapper.writeValueAsString(baseEvent);

    var eventData = new EventData(eventBody);

    eventData.getProperties().put("id", baseEvent.getId());
    eventData.getProperties().put("eventType", baseEvent.getEventType());
    eventData.getProperties().put("gameId", baseEvent.getGameId());
    eventData.getProperties().put("taskId", baseEvent.getTaskId());
    eventData.getProperties().put("tournamentId", baseEvent.getTournamentId());

    eventData.getProperties().put("source", baseEvent.getSource().name());

    // this will be empty string if no groupId is set (properties can't be null)
    var groupId = baseEvent.getGroupId() == null
        ? ""
        : baseEvent.getGroupId();
    eventData.getProperties().put("groupId", groupId);

    // this will be empty if there is no agent in the game
    var agentId = baseEvent.getAgentSubscriptionFilterValue() == null
        ? ""
        : baseEvent.getAgentSubscriptionFilterValue();
    eventData.getProperties().put("agentSubscriptionFilterValue", agentId);

    return eventData;
  }
}
