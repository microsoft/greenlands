"""Environment that abstracts the operations of a Game.
"""

from enum import Enum
import time
from typing import Any, Dict, List, Tuple

import gym

from agent_toolkit import logger
from agent_toolkit.event_aggregator import EventAggregator
from agent_toolkit.event_aggregator.local_game_state import LocalGameState
from agent_toolkit.event_callback_provider import EventCallbackProvider
from agent_toolkit.event_factory import RegisteredEvent

_LOGGER = logger.get_logger(__name__)


class TurnState(Enum):
    TURN_ABOUT_TO_START = "TURN_ABOUT_TO_START"
    TURN_IN_PROGRESS = "TURN_IN_PROGRESS"
    TURN_JUST_ENDED = "TURN_JUST_ENDED"


class GameEnvironment(gym.Env):
    """
    Environment that abstracts the operations of a Game.

    The GameEnvironment handles interactions between an Agent and the
    AgentToolkit, who will forward received events relative to the game.
    It keeps track of the Game State.
    """

    def __init__(
        self,
        role_id: str,
        callback_provider: EventCallbackProvider,
        initial_game_state: LocalGameState,
        episode_timeout_seconds: int = 10
    ) -> None:
        """Creates a new GameEnvironment

        Args:
            role_id (str): The id that identifies the agent's role. It is used to recognize which
                events are caused by the Agent.
            callback_provider (EventCallbackProvider) object that provides methods to call when
                an operation that may trigger an event has happened.
            initial_game_state (LocalGameState): the game state constructed from the initial data.
            episode_timeout_seconds (int, optional): how many seconds to wait
                until the game (episode) is finished. Defaults to 10.
        """
        self.callback_provider = callback_provider
        self.role_id = role_id
        self.episode_timeout_seconds = episode_timeout_seconds

        self.episode_start_time_ms: float = 0.0

        self.event_aggregator = EventAggregator(initial_game_state)

        self.target_states: List[LocalGameState] = []

        # observation_space is initially set to empty dict. It's up to user of this class to
        # properly set it
        self.observation_space = {}  # type: ignore

        self.is_first_step_in_turn = True

        self._turn_state: TurnState = TurnState.TURN_ABOUT_TO_START

    @property
    def no_op_action(self) -> Any:
        """Returns the no-op action for this environment."""

        raise NotImplementedError

    @property
    def game_state(self) -> LocalGameState:
        return self.event_aggregator.game_state

    def to_observation_space(self) -> Dict:
        """
        Returns the current state of the game following the format expected by the model,
        which is defined in `self.observation_space`.

        This method is called at a high frequency at every opportunity to make inference. The
        transformations made from LocalGameState to observation space should be as fast as
        possible.
        """

        # by default, it just returns a dict representation of self.game_state. It's up to the
        # subclasses to overwrite this method if they so desire.

        # Create dictionary of all non-private and non-callable class properties
        # https://stackoverflow.com/a/69088860/2234619
        # TODO this creates a one-level dictionary, where values can still be instances.
        # This which can create aliasing problems by giving references to the local game state.
        # We should implement a solution that recursively converts all objects to
        # dictionaries using this same algorithm.
        return dict(
            (key, value) for key, value in self.game_state.__dict__.items()
            if not callable(value)
            and not key.startswith('_')
        )

    def reset(self):
        """Builds the initial game state.

        Resets the environment to an initial state and returns an initial observation.
        Note that this function should not reset the environment's random
        number generator(s); random variables in the environment's state should
        be sampled independently between multiple calls to `reset()`. In other
        words, each call of `reset()` should yield an environment suitable for
        a new episode, independent of previous episodes.

        Returns:
            observation (object): Observation of the initial state. This will be a new LocalGameState
        """

        # This method cannot know what the subclasses of GameEnvironment will do, so by
        # default it doesn't do much. It's up to the subclasses to overwrite this method.
        self.episode_start_time_ms = time.time()

        return self.to_observation_space()

    def has_timedout(self) -> bool:
        return (time.time() - self.episode_start_time_ms >
                self.episode_timeout_seconds * 1000)

    def _was_event_caused_by_self(self, event: RegisteredEvent) -> bool:
        role_id = getattr(event, 'role_id', None)
        return role_id == self.role_id

    def apply_event(self, event: RegisteredEvent) -> None:
        self.event_aggregator.handle_event(event)

    def turn_is_starting(self) -> None:
        """
        Turn lifetime hook.

        Called when the agent's turn is about to start. The call to `step` after
        this hook is called is meant to be used to get the initial observation
        for the turn.
        """
        self._turn_state = TurnState.TURN_ABOUT_TO_START

    def turn_in_progress(self) -> None:
        """
        Turn lifetime hook.

        Called after each call to `step` after the first call in the turn, which
        is used to get the initial turn observation.
        """
        self._turn_state = TurnState.TURN_IN_PROGRESS

    def turn_is_ending(self) -> None:
        """
        Turn lifetime hook.

        Called when the agent's turn has just ended (after the execution of the
        last `step` for the current turn).
        """
        self._turn_state = TurnState.TURN_JUST_ENDED

    def step(self, action: Any) -> Tuple[Dict, float, bool, Dict]:
        """
        Run one single step of the environment's dynamics. Accepts the action we want to apply to
        the environment and returns a tuple.

        Returns tuple: (obs, reward, done, info)
        """
        raise NotImplementedError

    def close(self) -> None:
        """Override close in your subclass to perform any necessary cleanup.

        Environments will automatically `close()` themselves when garbage
        collected or when the program exits.
        """
        super().close()
