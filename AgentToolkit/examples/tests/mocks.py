
from typing import Callable, List

from agent_toolkit.clients.base_message_client import BaseMessageClient
from agent_toolkit.event_factory import RegisteredEvent


class MockEventClient(BaseMessageClient):

    def __init__(self) -> None:
        super().__init__()
        self.process_event = None
        self.sent_event_batches = []

    def subscribe(self, process_event: Callable[[RegisteredEvent], None]) -> None:
        self.process_event = process_event

    def send_events(self, events: List[RegisteredEvent]) -> None:
        self.sent_event_batches.append(events)

    def receive(self, event: RegisteredEvent) -> None:
        """Simulate the client receiving an event to manually invoke process_event during tests."""
        self.process_event(event)
