import time
import unittest
import uuid
from abc import ABC
from datetime import datetime
from typing import Type

from dotenv import load_dotenv
from gym import Wrapper
from greenlands_client.model import event_source
from greenlands_client.model.location import Location
from greenlands_client.model.platform_game_end_event import PlatformGameEndEvent
from greenlands_client.model.platform_game_start_event import PlatformGameStartEvent
from greenlands_client.model.platform_player_joins_game_event import PlatformPlayerJoinsGameEvent
from greenlands_client.model.platform_player_turn_change_event import PlatformPlayerTurnChangeEvent
from greenlands_client.model.turn_change_reason import TurnChangeReason

from examples.tests.mocks import MockEventClient
from agent_toolkit import (AgentToolkit, CommonEventsProperties, EventCallbackProvider,
                                      get_env_var)
from agent_toolkit.agent import Agent
from agent_toolkit.event_aggregator.local_game_state import LocalGameState
from agent_toolkit.game_environment import GameEnvironment
from agent_toolkit.wrappers.remote_task_loader import RemoteTaskLoader


class BaseLocalTestsSuite(unittest.TestCase, ABC):
    """Contains common methods for all test suites using local event exchanges.

    It is not intended to use as tests by itself, just extended.

    To inherit, replace the class attributes for the event client, game environment and
    agent class appropiate for your test case.
    """

    # Set up these variables with the classes to be tested by the suite.
    MOCK_EVENT_CLIENT_CLASS: Type[MockEventClient] = None
    AGENT_CLASS: Type[Agent] = None
    GAME_ENVIRONMENT_CLASS: Type[GameEnvironment] = None
    GAME_STATE_CLASS: Type[LocalGameState] = None
    # TODO create another class or more methods to allow local task loading

    def setUp(self) -> None:
        load_dotenv(".env")
        load_dotenv(".env.local", override=True)
        self.taskdata_container_url = get_env_var("TASKDATA_CONTAINER_URL")
        self.tournament_id = get_env_var("LOCAL_SERVER_TOURNAMENT_ID")
        self.task_id = get_env_var("LOCAL_SERVER_TASK_ID")

        self.mock_client = self.MOCK_EVENT_CLIENT_CLASS()

        self.service_id = "test_agent"
        self.service_role_id = "test_builder"
        self.other_player_role_id = "other_role"

        # defines who the role with the current turn is
        self.current_active_role_id = None

        # Every test can initialize its own AT with init_agent_toolkit,
        # but the tear down method will clean it and close all open games.
        self.agent_toolkit = None

        self.init_agent()

    def wait_for_agent_to_perform_actions(self):
        """
        Waits to give the game thread some time to have the agent process the
        provided actions.

        TODO: It would be better if this would wait for the agent to end its
        turn.
        """
        time.sleep(0.1)

    def init_agent(self):
        self.agent = self.AGENT_CLASS(
            agent_service_id=self.service_id,
            agent_service_role_id=self.service_role_id)

    def tearDown(self) -> None:
        if self.agent_toolkit is not None:
            while len(self.agent_toolkit._active_game_threads) > 0:
                game_id = list(self.agent_toolkit._active_game_threads.keys())[0]
                self.end_game(game_id)

    def create_game_environment(
        self,
        callback_provider: EventCallbackProvider,
        common_events_properties: CommonEventsProperties
    ):
        """Create a new game environment. It can be safely replaced on subclasses."""
        env = self.GAME_ENVIRONMENT_CLASS(
            role_id=common_events_properties.role_id,
            initial_game_state=self.GAME_STATE_CLASS(),
            callback_provider=callback_provider,
        )

        env = RemoteTaskLoader(
            env,
            common_events_properties.task_id,
            self.taskdata_container_url)

        return env

    def get_common_server_event_properties(self, game_id):
        return {
            'tournament_id': self.tournament_id,
            'task_id': self.task_id,
            'game_id': game_id,
            'source': event_source.EventSource(value="MinecraftPlugin"),
            'produced_at_datetime': datetime.utcnow().isoformat() + 'Z',
            'group_id': "LocalServer_GroupId",
            'id': str(uuid.uuid4()),
            'agent_subscription_filter_value': self.service_id,
        }

    def init_agent_toolkit(self, max_games):
        self.agent_toolkit = AgentToolkit(
            agent_service_id=self.service_id,
            agent_service_role_id=self.service_role_id,
            agent=self.agent,
            create_game_environment_fn=self.create_game_environment,
            client=self.mock_client,
            max_games=max_games,
            auto_rejoin_agent_queue=False,
            wait_for_game_environment=True,  # Only for testing purposes
        )

    def start_game(self, game_id):
        """Processes PlatformGameStartEvent and PlatformPlayerJoinsGameEvent.

        Simulates a new game has been started by the server and that the agent
        has joined it.

        Args:
            game_id (str): id of the game to be created.
        """
        player_id = 'p1'

        game_start_event = PlatformGameStartEvent(
            **self.get_common_server_event_properties(game_id),
            role_id=self.service_role_id,
        )
        self.mock_client.receive(game_start_event)
        join_game_event = PlatformPlayerJoinsGameEvent(
            **self.get_common_server_event_properties(game_id),
            player_id=player_id,
            role_id=self.service_role_id,
            spawn_location=Location(1., 1., 0., 0., 0.),
        )
        self.mock_client.receive(join_game_event)

    def change_turn(self, game_id, next_turn_role_id):
        """Processes PlatformPlayerTurnChangeEvent.

        Simulates a change in turn triggered by the server.

        Args:
            game_id (str): id of the game the agent is playing.
            next_turn_role_id (str): the role of the player that takes the
                starting turn.
        """
        turn_change_event = PlatformPlayerTurnChangeEvent(
            **self.get_common_server_event_properties(game_id),
            role_id=None,
            reason=TurnChangeReason(value="PLAYER_COMMAND"),
            next_active_role_id=next_turn_role_id,
            previous_active_role_id=self.current_active_role_id,
        )

        self.mock_client.receive(turn_change_event)

        self.current_active_role_id = next_turn_role_id

    def end_game(self, game_id):
        """Processes PlatformPlayerTurnChangeEvent.

        Simulates the server has ended the game.

        Args:
            game_id (str): id of the game that has ended.
        """
        game_end_event = PlatformGameEndEvent(
            **self.get_common_server_event_properties(game_id),
            role_id=self.service_role_id,
        )
        self.mock_client.receive(game_end_event)

    def get_game_state(self, game_id):
        if not game_id in self.agent_toolkit._active_game_threads:
            return None
        return self.agent_toolkit._active_game_threads[game_id].environment.game_state

    @staticmethod
    def get_base_game_environment_class(game_environment: GameEnvironment):
        """Removes wrappers from game_environment and returns the seed environment class.

        Environments inherited from gym can be nested inside wrappers that alter their
        functionality. As a result, the class of the game_environment is overriden with
        the new wrapper. This method returns the first environment class instantiated
        before adding the Wrappers.

        Args:
            game_environment (GameEnvironment): the game environment instance to analyze.

        Returns:
            The seed class of game_environment.
        """
        inner_game_environment = game_environment
        while isinstance(inner_game_environment, Wrapper):
            inner_game_environment = inner_game_environment.env
        return type(inner_game_environment)
