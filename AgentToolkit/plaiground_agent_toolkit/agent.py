"""Base class to represent an Agent.

Agents are wrappers around the underlying machine learning model that handles
the training loop and the reward function calculation.
"""
import threading
from abc import ABC, abstractmethod
from typing import Any

from plaiground_agent_toolkit.event_aggregator.local_game_state import LocalGameState


class Agent(ABC):
    def __init__(self, agent_service_id) -> None:
        self.__thread_lock = threading.Lock()

        # This is the key used to identify the agent.
        self.agent_service_id = agent_service_id

    # TODO eliminate the current_game_state parameter
    @abstractmethod
    def next_action(self, observation: Any, current_game_state: LocalGameState) -> Any:
        raise NotImplementedError

    def _thread_safe_next_action(self, observation: Any, current_game_state: LocalGameState) -> Any:
        with self.__thread_lock:
            return self.next_action(observation, current_game_state)

    def _thread_safe_restart(self, err: Exception) -> None:
        with self.__thread_lock:
            self.restart(err)

    def restart(self, err: Exception) -> None:
        """
        Called when the agent raises an exception so that agent can recover from
        it if necessary.

        It's not necessary to implement this callback but it's strongly
        recommended.
        """

    def game_end(self) -> None:
        """
        Called when a game ends.

        It's provided to you in case you need to do any clean-up at the agent
        level. However, it is recommended that the agent is markovian and does
        not need to be reset.
        """

    def turn_end(self):
        """
        Called when a turn ends.

        It's provided to you in case you need to do any clean-up at the agent
        level. However, it is recommended that the agent is markovian and does
        not need to be reset.
        """
