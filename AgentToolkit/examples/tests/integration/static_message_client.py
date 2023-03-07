import json
import compare_events
from datetime import datetime
from typing import List, Sequence
from greenlands_client.model import event_source
from agent_toolkit import (
    RegisteredEvent,
    GreenlandsMessageClient
)
from greenlands_client.model.platform_game_end_event import PlatformGameEndEvent
from dotenv import load_dotenv
from agent_toolkit.event_factory import GreenlandsEventFactory


class StaticMessageClient(GreenlandsMessageClient):
    def __init__(self,
                 agent_service_id: str,
                 agent_service_role_id: str,
                 publish_subscribe_connection_string: str,
                 consumer_group: str,
                 event_hub_name: str = 'games',
                 compare: bool = False
                 ) -> None:
        super().__init__(agent_service_id, publish_subscribe_connection_string, consumer_group, event_hub_name)

        self._agent_service_role_id = agent_service_role_id
        self.server_send_callback = None
        self.sent_events = []
        self.received_events = []
        #self._process_event_callback = None
        self.compare = compare


    def store_event_in_cache_for_comparison(self, event: RegisteredEvent) -> None:
        is_from_plugin = getattr(event, "source", None) == event_source.EventSource(value="MinecraftPlugin")
        if is_from_plugin:
            event_str = GreenlandsEventFactory.to_json(event)
            event_obj = json.loads(event_str)
            self.received_events.append(event_obj)

        if (isinstance(event, PlatformGameEndEvent)):
            msg = "---------------------------------------------\nDump of all events we got and sent during the game:\n"
            msg += "received events count: " + len(self.received_events).__str__() + "\n"
            msg += "sent events count: " + len(self.sent_events).__str__()
            msg += "\n---------------------------------------------"
            print(msg)

            obj = {
                'receivedEvents': self.received_events,
                'sentEvents':  self.sent_events
            }
            if self.compare == True:
                roleIds = compare_events.determine_role_ids(obj)
                move_indexes = compare_events.get_move_indexes(obj, roleIds)
                event_errors = compare_events.compare_moves(obj, move_indexes)
                print("---------------------------------")
                print("Finished - Number of move errors: " + len(event_errors).__str__())
                print("---------------------------------")
                compare_events.write_event_errors(event_errors)

            now = datetime.now()
            filename = 'sent_received_events_' + now.strftime('%m-%d-%y_%H-%M-%S') + '.json'
            with open(filename, 'w') as observationFile:
                json.dump(obj, fp=observationFile, indent=5)

            self.received_events.clear()
            self.sent_events.clear()


    def subscribe(self, process_event) -> None:

        def _process_event(event: RegisteredEvent):
            self.store_event_in_cache_for_comparison(event)
            process_event(event)

        super().subscribe(_process_event)


    def send_events(self, events: List[RegisteredEvent]) -> None:
        for event in events:
            event_str = GreenlandsEventFactory.to_json(event)
            event_obj = json.loads(event_str)
            self.sent_events.append(event_obj)

        super().send_events(events)
