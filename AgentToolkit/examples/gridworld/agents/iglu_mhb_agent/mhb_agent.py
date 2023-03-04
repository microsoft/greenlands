import sys
from typing import Dict, Optional

import numpy as np
from gym.spaces import Box, Dict, Discrete

from plaiground_agent_toolkit import Agent, LocalGameState, logger

# the iglu_mhb_agent expects the following folders to be in the PYTHONPATH. This
# is handled automatically whe running the agent from with the mhb repo folder,
# but since we're running it from the AT then we need to add them manually.
sys.path.append("examples/gridworld/agents/iglu_mhb_agent/iglu_2022_mhb_baseline")
sys.path.append(
    "examples/gridworld/agents/iglu_mhb_agent/iglu_2022_mhb_baseline/agents/")
sys.path.append(
    "examples/gridworld/agents/iglu_mhb_agent/iglu_2022_mhb_baseline/agents/mhb_baseline")

from iglu_2022_mhb_baseline.agents.mhb_baseline.agent import MultitaskHierarchicalAgent  # NOQA

_LOGGER = logger.get_logger(__name__)


class MhbAgent(Agent):
    def __init__(self, agent_service_id, action_space_name='walking'):
        super().__init__(agent_service_id)

        self.action_space_name = action_space_name

        self.restart(None)

    def __get_gridworld_action_space(self, action_space_name):
        """ Copied as-is from iglu_2022_mhb_baseline/agents/aicrowd_wrapper.py """

        assert isinstance(action_space_name, str)
        if action_space_name.lower() == 'walking':
            return Discrete(18)
        elif action_space_name.lower() == 'flying':
            return Dict({
                'movement': Box(low=-1, high=1, shape=(3,), dtype=np.float32),
                'camera': Box(low=-5, high=5, shape=(2,), dtype=np.float32),
                'inventory': Discrete(7),
                'placement': Discrete(3),
            })
        else:
            raise NotImplementedError(
                "action space name should be walking or flying")

    def restart(self, err: Optional[Exception]) -> None:
        """
        Start or restart the agent
        """
        self.agent = MultitaskHierarchicalAgent()
        self.agent.set_action_space(
            self.__get_gridworld_action_space(self.action_space_name)
        )

    def _clear_agent_state(self):
        # Clears internal state of the MHB agent to get it ready to play a new
        # game/turn
        if not self.agent.start:
            self.agent.clear_variable({})

    def turn_end(self):
        self._clear_agent_state()

    def game_end(self):
        self._clear_agent_state()

    def next_action(self, observation: dict, current_game_state: LocalGameState) -> int:
        action, user_terminations = self.agent.act(
            observation=observation,
            reward=None,
            # if True, this will reset the agent's internal state
            done=observation['done'],
            info=None
        )

        # if user_terminations is true then it means that the MHB agent has
        # finished executing all the actions it planned to fullfill what the NLP
        # model inferred the target state is after looking at the dialog
        if user_terminations:
            action = 18

        return action
