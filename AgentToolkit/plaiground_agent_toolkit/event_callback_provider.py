import math
import uuid
from datetime import datetime

from plaiground_client.model.block_place_event import BlockPlaceEvent
from plaiground_client.model.block_remove_event import BlockRemoveEvent
from plaiground_client.model.event_source import EventSource
from plaiground_client.model.location import Location
from plaiground_client.model.platform_player_leaves_game_event import PlatformPlayerLeavesGameEvent
from plaiground_client.model.platform_player_turn_change_event import PlatformPlayerTurnChangeEvent
from plaiground_client.model.player_chat_event import PlayerChatEvent
from plaiground_client.model.player_move_event import PlayerMoveEvent
from plaiground_client.model.turn_change_reason import TurnChangeReason

from plaiground_agent_toolkit.clients.base_message_client import BaseMessageClient
from plaiground_agent_toolkit.common_events_properties import CommonEventsProperties


class EventCallbackProvider:
    """Provides methods to call when an operation that may trigger an event has happened.

    Methods of this class should be called from the GameEnvironment or subclass when the
    game state is altered, to notify the Plaiground server of the change.

    NOTE: Not all calls to methods will cause events to be sent or published. For example, movement
    events are produced only when the distance between the last sent location and the new target
    location is sufficiently big. This significantly lowers the number of events sent.

    When sending other types of events, for example PlayerChatEvent, the position of all the
    involved players is sent first.
    """

    def __init__(self,
                 message_client: BaseMessageClient,
                 common_events_properties: CommonEventsProperties,
                 min_coordinate_distance_to_send_move_event: float = 0.2,
                 min_pitch_degrees_to_send_move_event: float = 5,
                 min_yaw_degrees_to_send_move_event: float = 10):
        """Creates new EventCallbackProvider

        Args:
            message_client (BaseMessageClient): client with send_events method.
            common_events_properties (CommonEventsProperties): Object that contains the properties
                that must be included in all events. The values of said properties identified the
                game using this callback provider.
            min_coordinate_distance_to_send_move_event (float, optional): minimal euclidean distance
                between the x, y and z coordinates of the last sent position and the new position to
                send. Defaults to 0.2.
            min_pitch_degrees_to_send_move_event (float, optional): minimal degree distance between
                the pitch of the last sent position and the pitch of the new position to send. Pitch
                refers to looking up or down movement, and varies between 90 and -90.
                Defaults to 5.
            min_yaw_degrees_to_send_move_event (float, optional): minimal degree distance between
                the yaw of the last sent position and the yaw of the new position to send. Yaw
                refers to looking left or right, and varies between 0 and 360.
                Defaults to 10.
        """
        self.client = message_client
        self.common_events_properties = common_events_properties
        self.last_sent_location = None
        self.min_coordinate_distance_to_send_move_event = min_coordinate_distance_to_send_move_event
        self.min_pitch_degrees_to_send_move_event = min_pitch_degrees_to_send_move_event
        self.min_yaw_degrees_to_send_move_event = min_yaw_degrees_to_send_move_event

    def _get_common_event_properties(self):
        return {
            'id': str(uuid.uuid4()),
            'produced_at_datetime': datetime.utcnow().isoformat() + 'Z',
            'source': EventSource(value="AgentService"),
            **self.common_events_properties.to_dict()
        }

    def _generate_player_move_event(self, player_location: Location):
        self.last_sent_location = player_location
        return PlayerMoveEvent(
            new_location=player_location,
            **self._get_common_event_properties()
        )

    def _is_move_distance_enough(self, new_location: Location) -> bool:
        """Returns whether new_location has varied enough from last_send_location."""
        if self.last_sent_location is None:
            return True
        # Check rotation angles
        # Pitch varies between 90 and -90
        pitch_diff = abs(self.last_sent_location.pitch - new_location.pitch)
        if pitch_diff >= self.min_pitch_degrees_to_send_move_event:
            return True

        # Yaw varies between 0 and 360, but 360 and 0 only have 1 degree of difference.
        yaw_diff = abs(self.last_sent_location.yaw - new_location.yaw)
        if yaw_diff > 180:  # It's shorter to go on the other direction of the circle.
            yaw_diff = 360 - yaw_diff
        if yaw_diff >= self.min_yaw_degrees_to_send_move_event:
            return True

        # Check euclidean x, y, z distance
        last_coordinates = (
            self.last_sent_location.x,
            self.last_sent_location.y,
            self.last_sent_location.z
        )
        new_coordinates = (new_location.x, new_location.y, new_location.z)
        euclidean_distance = math.dist(last_coordinates, new_coordinates)
        return euclidean_distance >= self.min_coordinate_distance_to_send_move_event

    def block_place(
        self,
        material: int,
        block_location: Location,
        player_location: Location
    ) -> None:
        move_event = self._generate_player_move_event(player_location)

        self.client.send_events([move_event, BlockPlaceEvent(
            material=material,
            location=block_location,
            **self._get_common_event_properties())])

    def block_remove(self, block_location: Location, player_location: Location) -> None:
        move_event = self._generate_player_move_event(player_location)
        self.client.send_events([
            move_event,
            BlockRemoveEvent(location=block_location, **self._get_common_event_properties())])

    def player_move(self, new_location: Location) -> None:
        # only update and send player location if new location is at least 'delta'
        # away from last location
        # need to also consider yaw and pitch, not only XYZ
        if self._is_move_distance_enough(new_location):
            move_event = self._generate_player_move_event(new_location)
            self.client.send_events([move_event])

    def player_chat(self, message: str, player_location: Location) -> None:
        move_event = self._generate_player_move_event(player_location)
        self.client.send_events([move_event, PlayerChatEvent(
            message=message,
            **self._get_common_event_properties()
        )])

    def player_leave(self) -> None:
        self.client.send_events(
            [PlatformPlayerLeavesGameEvent(**self._get_common_event_properties())])

    def turn_change(self, player_location: Location) -> None:
        move_event = self._generate_player_move_event(player_location)
        self.client.send_events([move_event, PlatformPlayerTurnChangeEvent(
            reason=TurnChangeReason(value="PLAYER_COMMAND"),
            **self._get_common_event_properties()
        )])
