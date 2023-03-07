import copy
from dataclasses import dataclass
from datetime import datetime
from typing import Dict, Tuple

from greenlands_client.model.block import Block
from greenlands_client.model.game_state import GameState
from greenlands_client.model.location import Location
from greenlands_client.model.player_state import PlayerState as GreenlandsClientPlayerState
from typing_extensions import TypeAlias

from agent_toolkit import logger
from agent_toolkit.event_factory import (GreenlandsEventFactory,
                                                    deserialize_greenlands_model)

_LOGGER = logger.get_logger(__name__)


def parse_location_string(location_string: str) -> Location:
    """Parses a location string into a Location object.
    """
    coordinates = location_string.replace("[", "").replace("]", "").split(",")

    if len(coordinates) != 5:
        _LOGGER.warning("Tried to parse a location string that doesn't have exactly "
                        f"5 dimensions: {location_string}")
        _LOGGER.warning("Missing coordinates will be set to 0, and any extra ones will be ignored.")

        if len(coordinates) > 5:
            coordinates = coordinates[:5]

        if len(coordinates) < 5:
            coordinates = coordinates + ["0.0"] * (5 - len(coordinates))

    x, y, z, pitch, yaw = [float(c) for c in coordinates]
    return Location(x, y, z, pitch, yaw)


RoleId: TypeAlias = str
PlayerId: TypeAlias = str


@dataclass()
class PlayerState:
    player_id: PlayerId
    role_id: RoleId
    location: Location

    def to_dict(self):
        return {
            'player_id': str(self.player_id),
            'role_id': str(self.role_id),
            'location': GreenlandsEventFactory.to_dict(self.location)
        }


@dataclass()
class ConversationActivity:
    role_id: RoleId
    message: str
    datetime: str

    def to_dict(self):
        return {
            'role_id': self.role_id,
            'message': self.message,
            'datetime': self.datetime
        }


class LocalGameState:
    """Represents what the Agent Toolkit thinks is the current state of a game"""

    def __init__(self) -> None:
        self.instructions = ""
        self.conversation_history: list[ConversationActivity] = []

        # initial states are set when the game begins and shouldn't be changed after that
        self.initial_player_states: Dict[RoleId, GreenlandsClientPlayerState] = {}

        self.player_states: Dict[RoleId, PlayerState] = {}
        self.active_role_id: str = ""

        self.blocks_in_world: Dict[Tuple[int, int, int], Block] = {}

    def set_initial_game_state(self,
                               initial_game_state: GameState,
                               complete_world_blocks: Dict[str, Block]) -> None:
        # set task instructions
        if initial_game_state.get('instructions', None) is not None:
            _LOGGER.debug("Setting task instructions")
            self.instructions = initial_game_state.get('instructions')

            if len(self.instructions) > 0:
                # utcnow().isoformat() does not include the 'Z' but `fromisoformat` expects the Z
                utc_iso_datetime = datetime.utcnow().isoformat() + 'Z'

                # Split instructions by line and add each line as chat message to simulate history
                chat_messages = self.instructions.split("\n")
                for message in chat_messages:
                    self.player_chat(role_id="", message=message, chat_datetime=utc_iso_datetime)

        # set initial player states
        if initial_game_state.get('player_states', None) is not None:
            _LOGGER.debug("Setting initial player states")
            for role_id, player_state in initial_game_state.get('player_states').items():
                self.initial_player_states[role_id] = player_state

        # set complete blocks in world
        _LOGGER.debug(
            f"Setting {len(complete_world_blocks)} complete blocks in world.")
        for location_string, block in complete_world_blocks.items():
            location = parse_location_string(location_string)
            # TODO: Check deserialization of Block type
            # AttributeError: 'dict' object has no attribute 'type'
            self.block_place_by_location(location, block['type'])

        if initial_game_state.get('world_state', None) is not None:
            if initial_game_state.get('world_state').get('block_changes', None) is not None:
                block_changes = initial_game_state.get(
                    'world_state').get('block_changes')
                _LOGGER.debug(
                    f"Setting {len(block_changes)} blocks from initial GameState.")
                for location_string, block in block_changes.items():
                    location = parse_location_string(location_string)
                    # TODO: Fix typing of block. It is Any, but should be Block
                    self.block_place_by_location(location, block['type'])

    def player_join(self, player_id: PlayerId, role_id: RoleId, location: Location):
        self.player_states[role_id] = PlayerState(
            player_id,
            role_id,
            location
        )

    def player_turn_change(self, role_id: RoleId):
        self.active_role_id = role_id

    def player_move(self, role_id: RoleId, new_location: Location):
        if role_id in self.player_states:
            self.player_states[role_id].location = new_location
        else:
            _LOGGER.warning(
                f"Attempted to update role {role_id} location, but player was not in player_states.")

    def player_chat(self, role_id: RoleId, message: str, chat_datetime: str):
        activity = ConversationActivity(role_id, message, chat_datetime)
        self.conversation_history.append(activity)

    def block_place_by_location(self, location: Location, material: int):
        self.block_place_by_coordinates(
            location.x,
            location.y,
            location.z,
            material
        )

    def block_place_by_coordinates(self, x, y, z, material: int):
        self.blocks_in_world[(int(x), int(y), int(z))] = Block(type=material)

    def block_remove_by_location(self, location: Location):
        self.block_remove_by_coordinates(
            location.x,
            location.y,
            location.z
        )

    def block_remove_by_coordinates(self, x, y, z):
        x = int(x)
        y = int(y)
        z = int(z)

        if (x, y, z) in self.blocks_in_world:
            del self.blocks_in_world[(x, y, z)]
            return

        _LOGGER.warning(f"Attempted delete block at location [{x},{y},{z}] " +
                        "but Agent game state did not have a block at that location. " +
                        "This likely indicates states are out of sync and is a bug.")

    def get_player_location(self, role_id):
        if role_id not in self.player_states:
            return None
        return self.player_states[role_id].location

    def __deepcopy__(self, memo):
        shallow_copy_of_self = copy.copy(self)

        shallow_copy_of_self.initial_player_states = deserialize_greenlands_model(
            GreenlandsEventFactory.to_json(shallow_copy_of_self.initial_player_states),
            GreenlandsClientPlayerState
        )

        fields_to_copy = dict(
            (key, value) for key, value in shallow_copy_of_self.__dict__.items()
            if not callable(value)
            and not key.startswith('_')
            and key not in ('initial_player_states')
        )

        for field_name, value in fields_to_copy.items():
            setattr(shallow_copy_of_self, field_name, copy.deepcopy(value))

        return shallow_copy_of_self

    def to_dict(self):
        return {
            "instructions": self.instructions,
            "conversation_history": [chat.to_dict() for chat in self.conversation_history],
            "player_states": {
                role: player_state.to_dict() for role, player_state in self.player_states.items()
            },
            "active_role_id": self.active_role_id,
            "blocks_in_world": {
                coordinates: Block.to_dict(block)
                for coordinates, block in self.blocks_in_world.items()
            }
        }