import time
from collections import defaultdict
from multiprocessing import Process, Queue
from queue import Empty
from typing import List

from azure.eventhub import EventData, EventHubProducerClient

from agent_toolkit import logger

_LOGGER = logger.get_logger(__name__)


def error_callback(send_events, something, error):
    _LOGGER.error(f"Error callback called with kwargs: {send_events} {something} {error}")


def success_callback(send_events: List, args):
    _LOGGER.debug(f"Successfully sent {len(send_events)} events")


class GreenlandsMessageProducer(Process):
    """
    This process is in charge of acting as a buffer of events that the AT wants
    to send to EH. It receives events and stores them in a queue. Every
    `self.wait_time` it creates batches for all events in the queue and sends
    them to EH.
    """

    def __init__(
        self,
        queue: Queue,
        publish_subscribe_connection_string: str,
        event_hub_name: str,
        wait_time=0.1,
    ):
        """
        Initializes a new instance of the GreenlandsMessageProducer class.

        Args:
            queue (Queue): The queue used to inform this process which are the
            EventData objects that need to be sent to EH.

            publish_subscribe_connection_string (str): Connection string for
            event hub.

            event_hub_name (str): Name of the event hub to send events to.

            wait_time (float, optional): Tells the process how much time to wait
            for new events to come in before building batches out of them.
            Defaults to 0.1.
        """

        super().__init__(
            name="GreenlandsMessageProducer_Process",
        )

        self.wait_time = wait_time
        self.__event_queue: Queue[EventData] = queue

        # NOTE: the default sync producer client takes a long time (~2s) when
        # sending a batch of events. To work around this, we're telling the
        # producer to use "buffered mode" with a very small wait time. The idea
        # of buffered mode is that the producer "buffers" events in memory for
        # the specified amount of time and then sends 1 or more batches with
        # those events. If new events are "sent" while the buffer is still in
        # memory, these are added to the batch and sent when "wait time" is
        # over. In this case, the small wait time ensures that producer client
        # sends the batch as soon as possible, removing the long 2s delay we
        # experience with the default constructor. For more information, check
        # the documentation here:
        # https://learn.microsoft.com/en-us/python/api/azure-eventhub/azure.eventhub.eventhubproducerclient?view=azure-python#parameters
        self.producer = EventHubProducerClient.from_connection_string(
            publish_subscribe_connection_string,
            eventhub_name=event_hub_name,
            buffered_mode=True,
            max_wait_time=0.001,  # don't wait before sending events
            on_success=success_callback,
            on_error=error_callback,
        )

    def _get_event_or_none(self):
        try:
            event = self.__event_queue.get(block=False)
            return event
        except Empty:
            return None

    def run(self):
        while True:
            # try to build batch of events that we want to send, separated by gameID
            event_data_per_game = defaultdict(list)
            while event := self._get_event_or_none():
                game_id: str = event.properties.get("gameId", "")

                # all sent events that are not AgentIsReady should have a game_id
                if event.properties.get("eventType") != "AgentIsReadyEvent":
                    assert len(game_id) > 0, \
                        f"Tried to send event of type {event.properties.get('eventType')} without a game id"

                event_data_per_game[game_id].append(event)

            # send batch for each game
            for game_id, batch in event_data_per_game.items():
                self._send_batch(game_id, batch)

            # wait some time to allow new events to trickle in
            time.sleep(self.wait_time)

    def _send_batch(self, game_id: str, event_data_array: List[EventData]) -> None:
        # suppose that all events are for the same gameId

        with self.producer:
            # Considering the low-latency requirement of agent communications, we'll
            # be sending 1 event per batch
            event_batch = self.producer.create_batch(partition_key=game_id)

            for event_data in event_data_array:
                try:
                    event_batch.add(event_data)
                except ValueError:
                    # if we overflow then send current batch and create a new one
                    self.producer.send_batch(event_batch)

                    _LOGGER.debug(f"Sending event batch with '{len(event_batch)}' events in it")
                    event_batch = self.producer.create_batch(partition_key=game_id)

            # Send any remaining events in the batch
            _LOGGER.debug(f"Sending event batch with '{len(event_batch)}' events in it")
            self.producer.send_batch(event_batch)
