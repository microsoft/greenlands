"""
"""
import threading
from abc import ABC, abstractmethod
from typing import Callable, List

from plaiground_agent_toolkit.event_factory import RegisteredEvent


class BaseMessageClient(ABC):
    """
    Abstract class representing Client for sending and receiving messages.
    """

    def __init__(self):
        self.__thread_lock = threading.Lock()

    @abstractmethod
    def send_events(self, events: List[RegisteredEvent]) -> None:
        raise NotImplementedError

    @abstractmethod
    def subscribe(self, process_event: Callable[[RegisteredEvent], None]) -> None:
        """
        Subscribes to messages, for each message received, converts the message to a known event,
        and calls process_event with the event. This method does NOT return. Subscribe will
        continue to listen for messages until the application is terminated.
        """
        raise NotImplementedError

    def _thread_safe_send_events(self, events: List[RegisteredEvent]) -> None:
        with self.__thread_lock:
            return self.send_events(events)
