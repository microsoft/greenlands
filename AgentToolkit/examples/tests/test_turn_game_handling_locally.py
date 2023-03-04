import unittest

from plaiground_client.model.agent_is_ready_event import AgentIsReadyEvent
from plaiground_client.model.player_chat_event import PlayerChatEvent

from examples.tutorial.agents.command_agent import ChatCommandAgent
from examples.tutorial.environments.command_game_environment import (
    CommandGameEnvironment, CommandLocalGameState)
from examples.tests.base_local_test_suite import BaseLocalTestsSuite
from examples.tests.mocks import MockEventClient
from plaiground_agent_toolkit.agent_toolkit import AgentToolkitUnexpectedExit
from plaiground_agent_toolkit.game_thread import GameThread


class TurnGameHandlingTests(BaseLocalTestsSuite):
    """Tests game creation, ending and turn handling with command agent.

    The AT is instantiated using LocalGameState and the actions are interpreted using
    SampleGameEnvironment.

    The agent used as sample is the CommandAgent, but no particular functionality is used
    besides changing turns.
    """

    MOCK_EVENT_CLIENT_CLASS = MockEventClient
    AGENT_CLASS = ChatCommandAgent
    GAME_ENVIRONMENT_CLASS = CommandGameEnvironment
    GAME_STATE_CLASS = CommandLocalGameState

    def test_send_agent_is_ready_event(self):
        """AgentToolkit can send AgentIsReadyEvent without failure.

        Additionally, this also tests the mechanism for sending events.
        """

        max_games = 1
        expected_num_agent_ready_events = max_games
        self.init_agent_toolkit(max_games)

        # In normal usage, the run method is not expected to exit and we throw error
        # However for testing we expect run to exit
        try:
            self.agent_toolkit.run()
        except AgentToolkitUnexpectedExit:
            pass

        self.assertEqual(1, len(self.mock_client.sent_event_batches))
        self.assertEqual(expected_num_agent_ready_events, len(self.mock_client.sent_event_batches[0]))
        event = self.mock_client.sent_event_batches[0][0]
        self.assertIsInstance(event, AgentIsReadyEvent)
        self.assertEqual(self.service_id, event.agent_service_id)

    def test_send_agent_is_ready_event_with_max_games(self):
        """AgentToolkit sends as many AgentIsReadyEvent as its max_games attribute."""
        max_games = 10
        expected_num_agent_ready_events = max_games
        self.init_agent_toolkit(max_games)

        # In normal usage, the run method is not expected to exit and we throw error
        # However for testing we expect run to exit
        try:
            self.agent_toolkit.run()
        except AgentToolkitUnexpectedExit:
            pass

        self.assertEqual(1, len(self.mock_client.sent_event_batches))
        self.assertEqual(expected_num_agent_ready_events, len(self.mock_client.sent_event_batches[0]))
        used_uuid = set()
        for event in self.mock_client.sent_event_batches[0]:
            self.assertNotIn(event.id, used_uuid)
            used_uuid.add(event.id)
            self.assertIsInstance(event, AgentIsReadyEvent)
            self.assertEqual(self.service_id, event.agent_service_id)

    # TODO test what should happen if game has started but the Agent has not joined
    # the game.
    def test_new_environment_after_join_game(self):
        """AT creates a GameThread and GameEnvironment when a PlatformGameStartEvent is received.
        """

        self.init_agent_toolkit(1)

        # In normal usage, the run method is not expected to exit and we throw error
        # However for testing we expect run to exit
        try:
            self.agent_toolkit.run()
        except AgentToolkitUnexpectedExit:
            pass

        game_id = '1'

        self.start_game(game_id)

        self.assertEqual(1, len(self.mock_client.sent_event_batches))
        self.assertIn(game_id, self.agent_toolkit._active_game_threads)
        self.assertIsInstance(
            self.agent_toolkit._active_game_threads[game_id],
            GameThread
        )
        self.assertEqual(
            self.get_base_game_environment_class(
                self.agent_toolkit._active_game_threads[game_id].environment),
            self.GAME_ENVIRONMENT_CLASS
        )

    def test_game_creation_limit(self):
        """AT does not create more games than specified by max_games attribute."""

        self.init_agent_toolkit(1)

        # In normal usage, the run method is not expected to exit and we throw error
        # However for testing we expect run to exit
        try:
            self.agent_toolkit.run()
        except AgentToolkitUnexpectedExit:
            pass

        game_id = '1'
        second_game_id = '2'

        self.start_game(game_id)
        self.start_game(second_game_id)

        # TODO should we have some sort of confirmation that the agent is
        # really playing the game?
        self.assertEqual(1, len(self.mock_client.sent_event_batches))
        self.assertEqual(1, len(self.agent_toolkit._active_game_threads))
        self.assertIn(game_id, self.agent_toolkit._active_game_threads)

    def test_apply_turn_change_event(self):
        """Game state is changed to reflect new active role and no more events are sent.
        """

        self.init_agent_toolkit(1)

        # In normal usage, the run method is not expected to exit and we throw error
        # However for testing we expect run to exit
        try:
            self.agent_toolkit.run()
        except AgentToolkitUnexpectedExit:
            pass

        game_id = '1'

        self.start_game(game_id)
        self.change_turn(game_id, self.service_role_id)

        # No events where emitted
        self.assertEqual(1, len(self.mock_client.sent_event_batches))
        self.assertEqual(1, len(self.agent_toolkit._active_game_threads))
        game_thread = self.agent_toolkit._active_game_threads[game_id]
        self.assertTrue(game_thread.wait_for_game_environment)
        # TODO: here we're reading data from another thread.. not safe!
        self.assertEqual(self.service_role_id, game_thread.environment.game_state.active_role_id)
        self.assertTrue(game_thread.environment.is_agent_turn())

    def test_no_action_outside_turn(self):
        """Test that agent does not emit events outside turn"""

        self.init_agent_toolkit(1)

        # In normal usage, the run method is not expected to exit and we throw error
        # However for testing we expect run to exit
        try:
            self.agent_toolkit.run()
        except AgentToolkitUnexpectedExit:
            pass

        game_id = '1'

        self.start_game(game_id)
        self.change_turn(game_id, self.other_player_role_id)

        # Record original Game State properties
        game_state = self.get_game_state(game_id)
        self.assertIsNotNone(game_state)
        original_conversation_len = len(game_state.conversation_history)
        original_location = game_state.get_player_location(self.service_role_id)

        command = "look:down"
        chat_event = PlayerChatEvent(
            **self.get_common_server_event_properties(game_id),
            role_id=self.other_player_role_id,
            message=command,
        )
        self.mock_client.receive(chat_event)
        self.assertEqual(1, len(self.mock_client.sent_event_batches))

        # Game state has been updated with conversation
        game_state = self.get_game_state(game_id)
        self.assertEqual(original_conversation_len + 1, len(game_state.conversation_history))
        self.assertEqual(game_state.conversation_history[-1].role_id, self.other_player_role_id)
        self.assertEqual(game_state.conversation_history[-1].message, command)
        # The command was not applied, Agent position was not changed
        new_player_location = game_state.get_player_location(self.service_role_id)
        self.assertEqual(original_location.pitch, new_player_location.pitch)

    def test_observation_contains_events_outside_turn(self):
        """When starting game, the agent observation contains events before agent turn starts.
        """

        self.init_agent_toolkit(1)

        # In normal usage, the run method is not expected to exit and we throw error
        # However for testing we expect run to exit
        try:
            self.agent_toolkit.run()
        except AgentToolkitUnexpectedExit:
            pass

        game_id = '1'

        self.start_game(game_id)
        # Override conversation history to have no initial messages!
        game_state = self.get_game_state(game_id)
        self.assertIsNotNone(game_state)
        game_state.conversation_history = []

        self.change_turn(game_id, self.other_player_role_id)

        # Record original Game State properties
        original_conversation_len = len(game_state.conversation_history)

        command = "echo:hello"
        chat_event = PlayerChatEvent(
            **self.get_common_server_event_properties(game_id),
            role_id=self.other_player_role_id,
            message=command,
        )
        self.mock_client.receive(chat_event)

        # Agent did not send message back outside its turn
        self.assertEqual(1, len(self.mock_client.sent_event_batches))
        self.assertEqual(original_conversation_len + 1, len(game_state.conversation_history))
        # Change to agent turn
        self.change_turn(game_id, self.service_role_id)

        # Agent now processes all events and applies chat action
        while(game_state.has_pending_commands()):
            pass
        self.assertEqual(original_conversation_len + 2, len(game_state.conversation_history))


if __name__ == '__main__':
    unittest.main()
