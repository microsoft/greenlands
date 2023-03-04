"""Classes to transform events to and from jsons string to OpenApi models.
"""

import json
from typing import Any, Dict, Type, TypeVar, Union

from plaiground_client import api_client, configuration, model_utils
from plaiground_client.model.agent_is_ready_event import AgentIsReadyEvent
from plaiground_client.model.block_place_event import BlockPlaceEvent
from plaiground_client.model.block_remove_event import BlockRemoveEvent
from plaiground_client.model.platform_game_end_event import PlatformGameEndEvent
from plaiground_client.model.platform_game_start_event import PlatformGameStartEvent
from plaiground_client.model.platform_player_joins_game_event import PlatformPlayerJoinsGameEvent
from plaiground_client.model.platform_player_leaves_game_event import PlatformPlayerLeavesGameEvent
from plaiground_client.model.platform_player_turn_change_event import PlatformPlayerTurnChangeEvent
from plaiground_client.model.platform_task_completed_event import PlatformTaskCompletedEvent
from plaiground_client.model.player_chat_event import PlayerChatEvent
from plaiground_client.model.player_move_event import PlayerMoveEvent
from plaiground_client.model.player_toggle_flight_event import PlayerToggleFlightEvent

T = TypeVar('T')


def deserialize_plaiground_model(model_dict: Dict, clazz: Type[T]) -> T:
    config = configuration.Configuration(discard_unknown_keys=True)
    return model_utils.validate_and_convert_types(
        model_dict,
        (clazz,),
        [],
        spec_property_naming=True,
        _check_type=True,
        configuration=config
    )


# TODO implement a cleaner registry of valid events that is in sync with
# the plaiground_client version.
# Events handled by the AgentToolkit. New events should be added to
# this list.
_REGISTERED_EVENTS = [
    # Agent events
    AgentIsReadyEvent,

    # Player events
    BlockPlaceEvent,
    BlockRemoveEvent,
    PlayerChatEvent,
    PlayerMoveEvent,
    PlayerToggleFlightEvent,

    # Platform events
    PlatformGameStartEvent,
    PlatformGameEndEvent,
    PlatformPlayerJoinsGameEvent,
    PlatformPlayerLeavesGameEvent,
    PlatformPlayerTurnChangeEvent,
    PlatformTaskCompletedEvent,
]

_REGISTERED_EVENTS_CLASSNAME_TO_CLASS_MAP = {
    _class.__name__: _class for _class in _REGISTERED_EVENTS
}

# TODO: Compute RegisteredEvent Union type from _REGISTERED_EVENTS class list
RegisteredEvent = Union[
    # AgentToolkit events
    AgentIsReadyEvent,

    # Player events
    BlockPlaceEvent,
    BlockRemoveEvent,
    PlayerChatEvent,
    PlayerMoveEvent,
    PlayerToggleFlightEvent,

    # Platform events
    PlatformGameStartEvent,
    PlatformGameEndEvent,
    PlatformPlayerJoinsGameEvent,
    PlatformPlayerLeavesGameEvent,
    PlatformPlayerTurnChangeEvent,
    PlatformTaskCompletedEvent,
]


class PlaigroundEventFactory:
    """Serialize OpenApiModels from json/to strings.

    OpenApiModel constructors assume a complex structure created by the same
    Api methods. It is not possible to reconstruct the model from a json string
    with this missing information using OpenApi methods. This helper class
    recursively walks the different nested models inside, creates them and
    returns a final model.

    The name of the event class to create is stored under the eventType key.
    This class is expected to inherit form OpenApiModel.
    """
    EVENT_TYPE_KEY = 'eventType'

    @classmethod
    def from_json(cls, json_dict) -> RegisteredEvent:
        if not isinstance(json_dict, dict):
            raise ValueError(
                f"Unable to convert json input to valid event: {json_dict}")

        return cls.from_dict(json_dict)

    @classmethod
    def from_dict(cls, event_dict: Dict[str, Any]) -> RegisteredEvent:
        # Get class reference
        if cls.EVENT_TYPE_KEY not in event_dict:
            raise ValueError(f"Unable to convert event dictionary to valid event. "
                             f"Missing {cls.EVENT_TYPE_KEY} key: {event_dict}")

        if event_dict[cls.EVENT_TYPE_KEY] not in _REGISTERED_EVENTS_CLASSNAME_TO_CLASS_MAP:
            raise ValueError(f"Unable to convert event dictionary to valid event. "
                             f"Unknown class {event_dict[cls.EVENT_TYPE_KEY]}: {event_dict}")

        event_class = _REGISTERED_EVENTS_CLASSNAME_TO_CLASS_MAP[
            event_dict[cls.EVENT_TYPE_KEY]
        ]

        # The cls.EVENT_TYPE_KEY (eventType) field is used by the OpenApi
        # functions to decide which class to instantiate. Since we are
        # doing that manually here, we need to delete the field. If we try
        # to instantiate an OpenApi Model from this event_dict, we would get
        # an error saying that the eventType field is read only, and we cannot
        # override it.
        del event_dict[cls.EVENT_TYPE_KEY]

        deserialized_data = deserialize_plaiground_model(event_dict, event_class)

        return deserialized_data

    @classmethod
    def to_dict(cls, event) -> Dict[str, Any]:
        event_dict = api_client.ApiClient.sanitize_for_serialization(event)
        event_dict[cls.EVENT_TYPE_KEY] = str(event.__class__.__name__)
        return event_dict

    @classmethod
    def to_json(cls, event) -> str:
        event_dict = cls.to_dict(event)
        return json.dumps(event_dict)
