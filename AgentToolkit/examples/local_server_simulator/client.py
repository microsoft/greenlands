import queue
from queue import Queue
from typing import Callable, List

from plaiground_client.model.base_event import BaseEvent

from agent_toolkit import BaseMessageClient, PlaigroundEventFactory, logger
from agent_toolkit.event_factory import RegisteredEvent

_LOGGER = logger.get_logger(__name__)


class LocalQueueClient(BaseMessageClient):
    """Auxiliary class that connects to a local server through Queues.

    This class is useful to test the environment with a local server.
    """

    def __init__(self, agent_service_id: str, serverbound_queue: Queue,
                 clientbound_queue: Queue,
                 read_timeout_milliseconds: int = 500) -> None:
        super().__init__()

        self.agent_service_id = agent_service_id
        self.serverbound_queue = serverbound_queue
        self.clientbound_queue = clientbound_queue
        self.read_timeout_milliseconds = read_timeout_milliseconds

    def send_events(self, events: List[BaseEvent]):
        for event in events:
            message_dict = PlaigroundEventFactory.to_dict(event)
            # TODO @atupini: the AT is expecting this value, so it should be part
            # of either the AT or the base client.
            message_dict['agent_subscription_filter_value'] = self.agent_service_id
            self.serverbound_queue.put(message_dict)
            # TODO clean up these prints...
            _LOGGER.info(
                f"Sent event {message_dict['eventType']} id: {event['id']}" +
                f" for game: ({message_dict.get('gameId', 'None')})" +
                f" and player: ({message_dict.get('playerId', 'None')})")

    def subscribe(self, process_event: Callable[[RegisteredEvent], None]) -> None:
        """Reads the queue for a new message.

        This method is blocking for at most self.read_timeout_milliseconds
        """
        while True:
            try:
                message_dict = self.clientbound_queue.get(block=True, timeout=self.read_timeout_milliseconds)
                _LOGGER.info(
                    f"Received event {message_dict['eventType']} for game: ({message_dict.get('gameId', 'None')})" +
                    f" id: {message_dict['id']}" +
                    (f" with message {message_dict['message']}" if 'message' in message_dict else ''))
                event = PlaigroundEventFactory.from_dict(message_dict)

                process_event(event)

            except queue.Empty:
                _LOGGER.warning(f"Attempted to read message from queue, but queue is empty")
