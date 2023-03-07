import os
import queue
import time
from queue import Queue
from typing import Callable

from agent_toolkit import logger

_LOGGER = logger.get_logger(__name__)


class LocalQueueServer:
    """Auxiliary class that simulates a server which can publish events.
    """
    MAX_PROCESS_LOOP_WITH_NO_MESSAGE = 10000
    # We mimic the ticking server by sleeping after each action
    TICK_FREQUENCY_SECONDS = 0.05

    def __init__(
        self,
        serverbound_queue: Queue,
        clientbound_queue: Queue,
        tournament_id: str,
        task_id: str,
        agent_service_role_id: str,
        process_message_function: Callable[[dict, Callable, dict], None],
    ) -> None:
        self.serverbound_queue = serverbound_queue
        self.clientbound_queue = clientbound_queue
        self.tournament_id = tournament_id
        self.task_id = task_id
        self.agent_service_role_id = agent_service_role_id
        self.process_message_fn = process_message_function
        # Ticks that passed since server start
        self.uptime_ticks = 0

    def run(self) -> None:
        process_attempts_without_message = 0
        try:
            while process_attempts_without_message < self.MAX_PROCESS_LOOP_WITH_NO_MESSAGE:
                if self.uptime_ticks % 50 == 0:
                    _LOGGER.debug(f"Server ticks: {self.uptime_ticks}. Attempts without message: {process_attempts_without_message}")

                message = self._receive_message()
                if message is not None:
                    config_dict = {
                        "tournament_id": self.tournament_id,
                        "task_id": self.task_id,
                        "agent_service_role_id": self.agent_service_role_id,
                    }
                    self.process_message_fn(message, self._send_message, config_dict)
                else:
                    process_attempts_without_message += 1

                time.sleep(self.TICK_FREQUENCY_SECONDS)
                self.uptime_ticks += 1

            _LOGGER.warning("Exiting main loop due to MAX_PROCESS_LOOP_WITH_NO_MESSAGE exceeded")

        except KeyboardInterrupt:
            # CTRL+C signal will be propagated from parent process and will
            # generate a KeyboardInterrupt exception on the child process
            _LOGGER.warning(f"Shutting down process {os.getpid()}")

    def _receive_message(self):
        """Reads the queue for a new event.

        Returns:
            Event: the read event, or None if queue is empty."""
        try:
            message_dict = self.serverbound_queue.get(block=False)
        except queue.Empty:
            return None  # No events were read in the queue
        _LOGGER.debug(
            f"Received event {message_dict['eventType']} id: {message_dict['id']} " +
            f"for game: ({message_dict.get('gameId', 'None')})" +
            (f" with message {message_dict['message']}" if 'message' in message_dict else '')
        )
        return message_dict

    def _send_message(self, message_dict) -> None:
        _LOGGER.info(
            f"Sent event {message_dict['eventType']} id: {message_dict['id']} " +
            f"for game: ({message_dict.get('gameId', 'None')})" +
            f" and player: ({message_dict.get('playerId', 'None')}) " +
            (f" for location ({message_dict['location']})" if 'location' in message_dict else "")
        )
        self.clientbound_queue.put(message_dict)

