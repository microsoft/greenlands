

import copy
import dataclasses
from datetime import datetime
from typing import Dict, List, Tuple

from examples.tutorial.environments import action_space
from examples.tutorial.environments.sample_game_environment import SampleGameEnvironment
from agent_toolkit.event_aggregator.local_game_state import LocalGameState
from agent_toolkit.event_callback_provider import EventCallbackProvider
from agent_toolkit.game_environment import TurnState


class AppendCommandsAction(action_space.Action):
    commands: List[str]
    commander_role_id: str


class CommandLocalGameState(LocalGameState):

    def __init__(self) -> None:
        super().__init__()
        self.next_command_index = 0

    def has_pending_commands(self):
        return self.next_command_index < len(self.conversation_history)

    def to_dict(self):
        """Returns a dictionary representation of the game state safe for aliasing."""
        return {
            'instructions': copy.copy(self.instructions),
            'conversation_history': [
                dataclasses.asdict(message) for message in self.conversation_history
            ],
            'player_states': {
                role_id: player_state.to_dict()
                for role_id, player_state in self.player_states.items()
            },
            'active_role_id': self.active_role_id,
            'next_command_index': self.next_command_index,

            # TODO: This is inneficient, but we somehow need to ensure that the
            # "agent" cannot edit the underlying blocks_in_world
            'blocks_in_world': copy.deepcopy(self.blocks_in_world),
        }


class CommandGameEnvironment(SampleGameEnvironment):
    """Environment to play games where the agent follows commands given by chats."""

    game_state: CommandLocalGameState

    def __init__(
            self,
            role_id: str,
            callback_provider: EventCallbackProvider,
            initial_game_state: CommandLocalGameState,
            episode_timeout_seconds: int = 10) -> None:
        super().__init__(role_id, callback_provider, initial_game_state, episode_timeout_seconds)
        self._is_turn_about_to_start = False

    @property
    def no_op_action(self) -> action_space.Action:
        return action_space.NoAction()

    def step(self, action: action_space.Action) -> Tuple[Dict, float, bool, Dict]:
        # if we're starting a new turn then just return the observation space
        # and don't do anything else
        if self._turn_state == TurnState.TURN_ABOUT_TO_START:
            return super().step(action)

        # If there are more commands in the chat history, mark the next one to be processed
        if len(self.game_state.conversation_history) > self.game_state.next_command_index:
            self.game_state.next_command_index += 1

        result = super().step(action)
        return result

    def to_observation_space(self) -> Dict:
        """
        Returns the current state of the game following the format expected by the model,
        which is defined in `self.observation_space`.
        """
        return self.game_state.to_dict()

    @property
    def registered_appliers(self):
        superclass_appliers = super().registered_appliers
        superclass_appliers[AppendCommandsAction] = self._append_command
        return superclass_appliers

    def _append_command(self, action: AppendCommandsAction):
        """Simulate a new command has arrived from the commander, without emmiting events"""
        utc_iso_datetime = datetime.utcnow().isoformat() + 'Z'
        self.game_state.player_chat(action.commander_role_id, action.message, utc_iso_datetime)
