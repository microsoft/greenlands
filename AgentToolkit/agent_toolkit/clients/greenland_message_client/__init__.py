import datetime
import json
import logging
from queue import Empty
from multiprocessing import Queue
from typing import Callable, List, Optional
from uuid import uuid4

import dateutil.parser
from azure.eventhub import EventData, EventHubProducerClient
from azure.eventhub.aio import EventHubConsumerClient, PartitionContext
from plaiground_client.model.event_source import EventSource

from agent_toolkit import logger
from agent_toolkit.clients.greenland_message_client._producer_process import GreenlandMessageProducer
from agent_toolkit.clients.greenland_message_client._consumer_process import GreenlandMessageConsumer
from agent_toolkit.clients.base_message_client import BaseMessageClient
from agent_toolkit.event_factory import PlaigroundEventFactory, RegisteredEvent

_LOGGER = logger.get_logger(__name__)

_AGENT_SOURCE = EventSource(value="AgentService")
_AGENT_SOURCE_STR = _AGENT_SOURCE.to_str()


def _add_property_if_not_empty(event_data: EventData,
                               property_name: str,
                               value: Optional[str]) -> None:
    if value is not None and value != "":
        event_data.properties[property_name] = value


class GreenlandMessageClient(BaseMessageClient):
    def __init__(
        self,
        agent_service_id: str,
        publish_subscribe_connection_string: str,
        consumer_group: str,
        event_hub_name: str = 'games',
    ) -> None:
        super().__init__()

        _LOGGER.debug(f"Creating GreenlandMessageClient for agent service id: {agent_service_id}")

        self._agent_service_id = agent_service_id

        # - The Consumer Process ONLY puts events on incoming queue, then
        #   GreenlandMessageClient#subscribe() takes events off of the queue
        #   and processes them.
        #
        # - For publishing the GreenlandMessageClient ONLY puts events on to
        #   outgoing queue and the Producer process monitors this queue and
        #   publishes any events put on it.

        self.__outgoing_event_queue: Queue[EventData] = Queue()
        self.__incoming_event_queue: Queue[EventData] = Queue()

        self.consumer_process = GreenlandMessageConsumer(
            queue=self.__incoming_event_queue,
            publish_subscribe_connection_string=publish_subscribe_connection_string,
            consumer_group=consumer_group,
            event_hub_name=event_hub_name,
        )
        self.consumer_process.start()

        self.producer_process = GreenlandMessageProducer(
            queue=self.__outgoing_event_queue,
            publish_subscribe_connection_string=publish_subscribe_connection_string,
            event_hub_name=event_hub_name,
        )
        self.producer_process.start()

    def send_events(self, events: List[RegisteredEvent]) -> None:
        for event in events:
            event_data = self._event_to_event_data(event)
            self.__outgoing_event_queue.put(event_data, block=False)

    def subscribe(self, process_event: Callable[[RegisteredEvent], None]) -> None:
        while True:
            # check that processess are still running
            if not self.consumer_process.is_alive():
                raise Exception("Consumer process has died")

            if not self.producer_process.is_alive():
                raise Exception("Producer process has died")

            try:
                incoming_event = self.__incoming_event_queue.get(block=True, timeout=5)
            except Empty:
                # if queue is empty, just continue
                continue

            deserialized_event = self._deserialize_event(incoming_event)
            if deserialized_event is None:
                # ignore this event if there was an error while deserializing it
                continue

            if _LOGGER.isEnabledFor(logging.DEBUG):
                produced_at_datetime = dateutil.parser.isoparse(
                    deserialized_event.produced_at_datetime).replace(tzinfo=datetime.timezone.utc)
                received_at_datetime = datetime.datetime.utcnow().replace(
                    tzinfo=datetime.timezone.utc)
                latency = received_at_datetime - produced_at_datetime

                _LOGGER.debug(
                    f"Received event: {deserialized_event.event_type} (ID: {deserialized_event.id}) produced at: {deserialized_event.produced_at_datetime} at {received_at_datetime} (latency: {latency})")

            try:
                process_event(deserialized_event)

            except BaseException as e:
                _LOGGER.exception(f"Error processing incoming event: {deserialized_event.to_str()}",
                                  e)

    @staticmethod
    def _deserialize_event(event: EventData) -> Optional[RegisteredEvent]:
        try:
            body_json = json.loads(event.body_as_str())

            decoded_event = PlaigroundEventFactory.from_json(body_json)
            return decoded_event

        except (ValueError, TypeError) as e:
            _LOGGER.exception(f"Error parsing incoming event: {str(event.body_as_str())}", e)
            return None

    def _event_to_event_data(self, event: RegisteredEvent) -> EventData:
        event.source = _AGENT_SOURCE
        event.id = str(uuid4())

        event.event_type = event.__class__.__name__
        event.agent_subscription_filter_value = self._agent_service_id

        if event.produced_at_datetime is None:
            event.produced_at_datetime = datetime.datetime.utcnow().isoformat() + 'Z'

        message_json = PlaigroundEventFactory.to_json(event)
        event_data = EventData(message_json)

        event_data.properties["source"] = _AGENT_SOURCE_STR

        _add_property_if_not_empty(event_data, "eventType", event.event_type)
        _add_property_if_not_empty(event_data, "id", event.id)
        _add_property_if_not_empty(event_data, "gameId", event.game_id)
        _add_property_if_not_empty(event_data, "taskId", event.task_id)
        _add_property_if_not_empty(event_data, "tournamentId", event.tournament_id)
        _add_property_if_not_empty(event_data, "producedAtDatetime", event.produced_at_datetime)
        _add_property_if_not_empty(event_data, "groupId", getattr(event, "group_id", None))

        _add_property_if_not_empty(event_data, "agentSubscriptionFilterValue",
                                   self._agent_service_id)

        _LOGGER.debug(f"Preparing to send event data: {str(event_data)}")

        return event_data
