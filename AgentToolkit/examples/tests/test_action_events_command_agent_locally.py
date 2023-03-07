import time
import unittest

from greenlands_client.model.player_chat_event import PlayerChatEvent
from greenlands_client.model.player_move_event import PlayerMoveEvent

from examples.tutorial.agents.command_agent import ChatCommandAgent
from examples.tutorial.environments.command_game_environment import (
    CommandGameEnvironment, CommandLocalGameState)
from examples.tests.base_local_test_suite import BaseLocalTestsSuite
from examples.tests.mocks import MockEventClient
from agent_toolkit.agent_toolkit import AgentToolkitUnexpectedExit


class CommandAgentActionEventsTests(BaseLocalTestsSuite):
    """Tests game state changes and event generation for ChatCommandAgent.

    The AT is instantiated using LocalGameState and the actions are interpreted using
    SampleGameEnvironment.
    """

    MOCK_EVENT_CLIENT_CLASS = MockEventClient
    AGENT_CLASS = ChatCommandAgent
    GAME_ENVIRONMENT_CLASS = CommandGameEnvironment
    GAME_STATE_CLASS = CommandLocalGameState

    def _give_command(self, game_id, command):
        chat_event = PlayerChatEvent(
            **self.get_common_server_event_properties(game_id),
            role_id=self.other_player_role_id,
            message=command,
        )
        self.mock_client.receive(chat_event)

    def test_position_events_with_min_distance_thresholds(self):
        """Agent only sends PlayerMoveEvent when change in position is above a threshold.

        Three different measuments are tested: pitch, yaw and position change. An action
        that causes a small delta in Location is sent, which should not produce an event. A second
        action that causes a delta in Location exceeding the threashold of CallbackEventProvider
        is sent, which should produce a PlayerMoveEvent with the compound effect of both actions.
        """

        max_games = 1
        num_agent_ready_events = max_games
        self.init_agent_toolkit(max_games)

        try:
            self.agent_toolkit.run()
        except AgentToolkitUnexpectedExit:
            pass

        game_id = '1'

        self.start_game(game_id)
        # Set a specific minimum distance for to send a movement event for testing
        game_thread = self.agent_toolkit._active_game_threads[game_id]
        callback_provider = game_thread.environment.callback_provider
        callback_provider.min_yaw_degrees_to_send_move_event = 45

        # Send an initial move event to generate a starting location.
        self.change_turn(game_id, self.other_player_role_id)
        self._give_command(game_id, "move:west")

        # Record initial state
        game_state = self.get_game_state(game_id)
        self.assertIsNotNone(game_state)
        current_location = game_thread.environment.game_state.get_player_location(
            self.service_role_id)
        self.assertIsNotNone(current_location)

        # Small YAW movement
        original_yaw = current_location.yaw

        degrees1 = 10
        self._give_command(game_id, f"rotate:left:{degrees1}")
        sent_event_batches_expected_count = num_agent_ready_events + 1


        # Change turn to the agent so it can perform its actions
        self.change_turn(game_id, self.service_role_id)
        self.wait_for_agent_to_perform_actions()

        # Game state has been updated with new position
        new_player_location = game_thread.environment.game_state.get_player_location(
            self.service_role_id)
        self.assertIsNotNone(new_player_location)

        expected_yaw = (original_yaw - degrees1 + 360) % 360
        self.assertEqual(expected_yaw, new_player_location.yaw)
        # NO new confirmation event was sent - First event contained the initial position
        self.assertEqual(
            sent_event_batches_expected_count,
            len(self.mock_client.sent_event_batches))

        # YAW movements
        # Big YAW movement
        self.change_turn(game_id, self.other_player_role_id)

        degrees2 = 50
        self._give_command(game_id, f"rotate:left:{degrees2}")
        sent_event_batches_expected_count += 1

        # Game state has been updated with new position
        self.change_turn(game_id, self.service_role_id)
        self.wait_for_agent_to_perform_actions()

        new_player_location = game_thread.environment.game_state.get_player_location(
            self.service_role_id)
        self.assertIsNotNone(new_player_location)

        self.assertEqual(
            ((original_yaw - degrees1 - degrees2 + 360) % 360),
            new_player_location.yaw)

        # A new event was sent
        self.assertEqual(
            sent_event_batches_expected_count,
            len(self.mock_client.sent_event_batches))

        # Only one event was sent this batch
        self.assertEqual(1, len(self.mock_client.sent_event_batches[-1]))
        self.assertIsInstance(self.mock_client.sent_event_batches[-1][0], PlayerMoveEvent)

        # PITCH movements
        callback_provider.min_pitch_degrees_to_send_move_event = 60
        # First PITCH movement, only alters pitch by 45 degrees
        original_pitch = current_location.pitch

        # Lower agent pitch 45 degrees
        self.change_turn(game_id, self.other_player_role_id)
        self._give_command(game_id, "look:up")

        # Game state has been updated with new position
        self.change_turn(game_id, self.service_role_id)
        self.wait_for_agent_to_perform_actions()

        new_player_location = game_thread.environment.game_state.get_player_location(
            self.service_role_id)
        self.assertIsNotNone(new_player_location)

        self.assertEqual((max(original_pitch - 45, -90)), new_player_location.pitch)
        # NO new confirmation event was sent - First event contained the initial position
        self.assertEqual(
            sent_event_batches_expected_count,
            len(self.mock_client.sent_event_batches))

        # Second PITCH movement, alters original pitch by at most 90 degrees.
        self.change_turn(game_id, self.other_player_role_id)
        self._give_command(game_id, "look:up")
        sent_event_batches_expected_count += 1

        # Game state has been updated with new position
        self.change_turn(game_id, self.service_role_id)
        self.wait_for_agent_to_perform_actions()

        new_player_location = game_thread.environment.game_state.get_player_location(
            self.service_role_id)
        self.assertIsNotNone(new_player_location)

        self.assertEqual(max(original_pitch - 90, -90), new_player_location.pitch)
        # A new event was sent
        self.assertEqual(
            sent_event_batches_expected_count,
            len(self.mock_client.sent_event_batches))
        # Only one event was sent this batch
        self.assertEqual(1, len(self.mock_client.sent_event_batches[-1]))
        self.assertIsInstance(self.mock_client.sent_event_batches[-1][0], PlayerMoveEvent)

        # POSITION changes
        # Change the sensitivity of the callback provider to need 3 move commands
        # before sending event.
        callback_provider.min_coordinate_distance_to_send_move_event = \
            self.agent.position_movement_distance * 3

        # Frist and second Y direction movement
        original_y = current_location.y

        self.change_turn(game_id, self.other_player_role_id)

        self._give_command(game_id, "move:up")
        self._give_command(game_id, "move:up")

        self.change_turn(game_id, self.service_role_id)
        self.wait_for_agent_to_perform_actions()

        # Game state has been updated with new position
        new_player_location = game_thread.environment.game_state.get_player_location(
            self.service_role_id)
        self.assertEqual(
            original_y + self.agent.position_movement_distance * 2,
            new_player_location.y)
        # NO new confirmation event was sent - First event contained the initial position
        self.assertEqual(
            sent_event_batches_expected_count,
            len(self.mock_client.sent_event_batches))

        # Second and third Y direction movement
        self.change_turn(game_id, self.other_player_role_id)

        self._give_command(game_id, "move:up")
        sent_event_batches_expected_count += 1

        # Game state has been updated with new position
        self.change_turn(game_id, self.service_role_id)
        self.wait_for_agent_to_perform_actions()

        new_player_location = game_thread.environment.game_state.get_player_location(
            self.service_role_id)
        self.assertIsNotNone(new_player_location)

        self.assertEqual(
            original_y + self.agent.position_movement_distance * 3,
            new_player_location.y)
        # A new event was sent
        self.assertEqual(
            sent_event_batches_expected_count,
            len(self.mock_client.sent_event_batches))
        # Only one event was sent this batch
        self.assertEqual(1, len(self.mock_client.sent_event_batches[-1]))
        self.assertIsInstance(self.mock_client.sent_event_batches[-1][0], PlayerMoveEvent)

    def test_send_position_with_other_events(self):
        """Tests the current agent position is always sent before other events."""

        max_games = 1
        num_agent_ready_events = max_games
        self.init_agent_toolkit(max_games)

        # In normal usage, the run method is not expected to exit and we throw error
        # However for testing we expect run to exit
        try:
            self.agent_toolkit.run()
        except AgentToolkitUnexpectedExit:
            pass

        game_id = '1'
        self.start_game(game_id)
        self.change_turn(game_id, self.other_player_role_id)

        # Send an initial move event to generate a starting location.
        self._give_command(game_id, "move:west")
        sent_event_batches_expected_count = num_agent_ready_events + 1

        # Record initial state
        self.change_turn(game_id, self.service_role_id)
        self.wait_for_agent_to_perform_actions()

        game_state = self.get_game_state(game_id)
        self.assertIsNotNone(game_state)

        # Emit non-movement command
        self.change_turn(game_id, self.other_player_role_id)

        message = "Hello"
        self._give_command(game_id, f"echo:{message}")
        sent_event_batches_expected_count += 1

        # A new batch of events was sent, with 2 events
        self.change_turn(game_id, self.service_role_id)
        self.wait_for_agent_to_perform_actions()

        self.assertEqual(
            sent_event_batches_expected_count,
            len(self.mock_client.sent_event_batches))
        self.assertEqual(2, len(self.mock_client.sent_event_batches[-1]))
        self.assertIsInstance(self.mock_client.sent_event_batches[-1][0], PlayerMoveEvent)
        self.assertIsInstance(self.mock_client.sent_event_batches[-1][1], PlayerChatEvent)

    def test_incorrect_commands(self):
        """When the command cannot be parsed, the no event is sent."""

        max_games = 1
        num_agent_ready_events = max_games
        self.init_agent_toolkit(max_games)

        # In normal usage, the run method is not expected to exit and we throw error
        # However for testing we expect run to exit
        try:
            self.agent_toolkit.run()
        except AgentToolkitUnexpectedExit:
            pass

        game_id = '1'
        self.start_game(game_id)
        self.change_turn(game_id, self.service_role_id)

        # Send an initial move event to generate a starting location.
        self._give_command(game_id, "move:xyz")

        # No events were sent
        expected_num_events_sent = num_agent_ready_events
        self.assertEqual(expected_num_events_sent, len(self.mock_client.sent_event_batches))


if __name__ == '__main__':
    unittest.main()
