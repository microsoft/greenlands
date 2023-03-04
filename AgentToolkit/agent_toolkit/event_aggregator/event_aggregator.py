"""Classes that modify the GameState based on received events from MCPlugin.
"""
from functools import cached_property
from typing import Dict

from plaiground_client.model.block import Block
from plaiground_client.model.block_place_event import BlockPlaceEvent
from plaiground_client.model.block_remove_event import BlockRemoveEvent
from plaiground_client.model.game_state import GameState
from plaiground_client.model.platform_player_joins_game_event import PlatformPlayerJoinsGameEvent
from plaiground_client.model.platform_player_turn_change_event import PlatformPlayerTurnChangeEvent
from plaiground_client.model.player_chat_event import PlayerChatEvent
from plaiground_client.model.player_move_event import PlayerMoveEvent

from agent_toolkit import logger
from agent_toolkit.event_aggregator.local_game_state import LocalGameState
from agent_toolkit.event_factory import RegisteredEvent

_LOGGER = logger.get_logger(__name__)


class EventAggregator:
    """Applies changes to the GameState from Events."""

    def __init__(self, game_state: LocalGameState) -> None:
        self.game_state = game_state

    @cached_property
    def event_classname_to_gamestate_handler_map(self):
        # All values must be functions that receive the event as argument
        return {
            PlatformPlayerJoinsGameEvent: self._player_join_event,
            PlatformPlayerTurnChangeEvent: self._player_turn_change_event,
            PlayerMoveEvent: self._player_move_event,
            PlayerChatEvent: self._player_chat_event,
            BlockPlaceEvent: self._block_place_event,
            BlockRemoveEvent: self._block_remove_event,
        }

    def handle_event(self, event: RegisteredEvent) -> None:
        if event.__class__ not in self.event_classname_to_gamestate_handler_map:
            _LOGGER.debug(f"{event.__class__.__name__} was received, but there is not a handler " +
                          "for that class registered. Ignoring event.")
            return

        handler = self.event_classname_to_gamestate_handler_map[event.__class__]

        # noinspection PyArgumentList
        handler(event)

    def _player_join_event(self, event: PlatformPlayerJoinsGameEvent) -> None:
        self.game_state.player_join(
            event.player_id,
            event.role_id,
            event.spawn_location
        )

    def _player_turn_change_event(self, event: PlatformPlayerTurnChangeEvent) -> None:
        self.game_state.player_turn_change(event.next_active_role_id)

    def _player_move_event(self, event: PlayerMoveEvent) -> None:
        self.game_state.player_move(event.role_id, event.new_location)

    def _player_chat_event(self, event: PlayerChatEvent) -> None:
        self.game_state.player_chat(event.role_id, event.message, event.produced_at_datetime)

    def _block_place_event(self, event: BlockPlaceEvent) -> None:
        self.game_state.block_place_by_location(event.location, event.material)

    def _block_remove_event(self, event: BlockRemoveEvent) -> None:
        self.game_state.block_remove_by_location(event.location)
