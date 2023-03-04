from typing import Dict

from examples.gridworld.environment.gridworld_game_environment import GridWorldGameEnvironment
from plaiground_agent_toolkit.game_environment import TurnState


class GridworldCommandGameEnvironment(GridWorldGameEnvironment):
    def __init__(self, **kwargs) -> None:
        super().__init__(**kwargs)

        self.next_command_index = 0
        self.was_last_action_end_turn = False

    def to_observation_space(self) -> Dict:
        obs = super().to_observation_space()

        obs["next_command_index"] = self.next_command_index
        obs["was_last_action_end_turn"] = self.was_last_action_end_turn

        return obs

    def step(self, action: int):
        # if we're starting a new turn then just return the observation space
        # and don't do anything else
        if self._turn_state == TurnState.TURN_ABOUT_TO_START:
            return super().step(action)

        # If there are more commands in the chat history, mark the next one to
        # be processed.
        if len(self.game_state.conversation_history) > self.next_command_index:
            self.next_command_index += 1

        # Update state if last action was end_turn
        if action == 18:  # action == end turn
            self.was_last_action_end_turn = True
        elif action == 0 and self.was_last_action_end_turn:
            # if there are any noop actions after we ended the turn then ignore
            # them and assume we just ended the turn
            self.was_last_action_end_turn = True
        else:
            self.was_last_action_end_turn = False

        return super().step(action)
