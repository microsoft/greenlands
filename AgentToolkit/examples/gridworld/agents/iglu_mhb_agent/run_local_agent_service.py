"""Example script that runs a CommandAgent in a local simulator.

Kill the process with CTRL+C. The signal will propagate to child processes.
"""

import copy
import logging
import os
import uuid
from datetime import datetime
from hashlib import md5
from pathlib import Path

import numpy
from dotenv import load_dotenv
from plaiground_client.model import event_source
from plaiground_client.model.agent_is_ready_event import AgentIsReadyEvent
from plaiground_client.model.location import Location
from plaiground_client.model.platform_game_end_event import PlatformGameEndEvent
from plaiground_client.model.platform_game_start_event import PlatformGameStartEvent
from plaiground_client.model.platform_player_joins_game_event import PlatformPlayerJoinsGameEvent
from plaiground_client.model.platform_player_leaves_game_event import PlatformPlayerLeavesGameEvent
from plaiground_client.model.platform_player_turn_change_event import PlatformPlayerTurnChangeEvent
from plaiground_client.model.turn_change_reason import TurnChangeReason

from examples.gridworld.agents.iglu_mhb_agent.mhb_agent import MhbAgent
from examples.gridworld.environment.gridworld_game_environment import GridWorldGameEnvironment
from examples.gridworld.environment.task import Task
from examples.gridworld.environment.world import World
from examples.gridworld.environment.wrappers.common_wrappers import TurnEndObservationWrapper
from examples.gridworld.environment.wrappers.iglu_format_task_converter import \
    IGLUFormatTaskConverterWrapper
from examples.local_server_simulator import LocalConnectionSimulator
from agent_toolkit import (AgentToolkit, BaseMessageClient, CommonEventsProperties,
                                      EventCallbackProvider, PlaigroundEventFactory, logger)
from agent_toolkit.utils import get_env_var
from agent_toolkit.wrappers.remote_task_loader import RemoteTaskLoader

logger_blocklist = [
    'uamqp',
    'numba'
]

_LOGGER = logger.get_logger(__name__)

for module in logger_blocklist:
    logging.getLogger(module).setLevel(logging.WARNING)

logging.getLogger('agent_toolkit').setLevel(logging.DEBUG)


def run_agent_service(event_client: BaseMessageClient,
                      agent_service_id: str,
                      agent_service_role_id: str,
                      taskdata_container_url: str) -> None:
    """Creates agent and sets it to play through event_client."""

    def create_game_environment(
        callback_provider: EventCallbackProvider,
        common_events_properties: CommonEventsProperties
    ):
        env = GridWorldGameEnvironment(
            role_id=common_events_properties.role_id,
            callback_provider=callback_provider,
            game_state=World(),
            select_and_place=True,  # when changing block selection, agent will try to place a block
            discretize=True,
            max_steps=500,
            target_in_obs=False,
            render=True,
            render_size=(64, 64),
            vector_state=True
        )

        custom_grid = numpy.ones((9, 11, 11))
        env.set_task(Task("", custom_grid))

        env = RemoteTaskLoader(
            env,
            common_events_properties.task_id,
            taskdata_container_url)

        env = IGLUFormatTaskConverterWrapper(env)

        env = TurnEndObservationWrapper(env)

        return env

    agent = MhbAgent(agent_service_id=agent_service_id)

    at = AgentToolkit(
        agent_service_id=agent_service_id,
        agent_service_role_id=agent_service_role_id,
        create_game_environment_fn=create_game_environment,
        agent=agent,
        client=event_client,
        max_games=1,
        auto_rejoin_agent_queue=False
    )

    try:
        at.run()
    except KeyboardInterrupt:
        # CTRL+C signal will be propagated from parent process and will
        # generate a KeyboardInterrupt exception on the child process
        _LOGGER.info(f"Shutting down client process {os.getpid()}")


some_other_role_id = str(uuid.uuid4())
some_other_player_id = str(uuid.uuid4())


def process_message(
    received_message,
    send_message_fn,
    config_dict
) -> None:
    """Hardcoded server responses to each type of Event message.
    """

    received_event = PlaigroundEventFactory.from_dict(received_message)

    common_event_properties = {
        'tournament_id': config_dict['tournament_id'],
        'task_id': config_dict['task_id'],
        'game_id': received_event.game_id,
        'source': event_source.EventSource(value="MinecraftPlugin"),
        'produced_at_datetime': datetime.utcnow().isoformat() + 'Z',
        'group_id': "LocalServer_GroupId",
        'id': str(uuid.uuid4()),
        'agent_subscription_filter_value': None,
    }

    # NOTE: These lines are necessary for events PlatformPlayerLeavesGameEvent
    # and PlatformGameEndEvent, but for the others is not. I don't know why
    common_event_properties['agent_subscription_filter_value'] = (
        received_event.agent_subscription_filter_value
        if hasattr(received_event, "agent_subscription_filter_value") else None)

    # If agent is ready, publish a sequence of events that simulate a game
    if isinstance(received_event, AgentIsReadyEvent):
        # Start the game
        game_id = str(uuid.uuid4())
        common_event_properties['game_id'] = game_id

        new_event = PlatformGameStartEvent(
            **common_event_properties,
            role_id=None,
        )

        send_message_fn(PlaigroundEventFactory.to_dict(new_event))
        agent_key = _get_agent_key(
            game_id, config_dict['agent_service_role_id'])

        # Simulate Agent joining the game
        new_event = PlatformPlayerJoinsGameEvent(
            **common_event_properties,
            player_id=agent_key,
            role_id=config_dict['agent_service_role_id'],
            spawn_location=Location(0., 1., 0., 0., 0.),
        )

        send_message_fn(PlaigroundEventFactory.to_dict(new_event))

        # Simulate a different role joining the game
        new_event = PlatformPlayerJoinsGameEvent(
            **common_event_properties,
            player_id=agent_key,
            spawn_location=Location(2., 0., 2., 0., 0.),
            role_id=some_other_role_id,
        )

        send_message_fn(PlaigroundEventFactory.to_dict(new_event))

        # Make it so that the "other" has first turn, to simulate what would happen in a real game
        new_event = PlatformPlayerTurnChangeEvent(
            **common_event_properties,
            reason=TurnChangeReason("PLATFORM_GAME_START"),
            next_active_role_id=some_other_role_id,
            previous_active_role_id=None
        )

        send_message_fn(PlaigroundEventFactory.to_dict(new_event))

        # Change turn to agent so it can start processing events
        new_event = PlatformPlayerTurnChangeEvent(
            **common_event_properties,
            reason=TurnChangeReason("PLAYER_COMMAND"),
            next_active_role_id=config_dict['agent_service_role_id'],
            previous_active_role_id=some_other_role_id
        )

        send_message_fn(PlaigroundEventFactory.to_dict(new_event))

    elif isinstance(received_event, PlatformPlayerLeavesGameEvent):
        # If agent leaves the game, we also simulate the other role leaving the game and then terminating the game
        if received_event.role_id == config_dict['agent_service_role_id']:
            new_event = PlatformPlayerLeavesGameEvent(
                **common_event_properties,
                role_id=some_other_role_id,
            )

            send_message_fn(PlaigroundEventFactory.to_dict(new_event))

            new_event = PlatformGameEndEvent(
                **common_event_properties,
            )

            send_message_fn(PlaigroundEventFactory.to_dict(new_event))

    # For all other events, simulate plugin behavior.
    # Assume it has been applied to the world and re-publish event with Plugin source.
    else:
        # Should be deepcopy to avoid modifying the original event, but attempt files
        # TypeError: PlayerChatEvent._from_openapi_data() missing 6 required positional arguments: ...
        new_event = PlaigroundEventFactory.from_dict(
            copy.deepcopy(PlaigroundEventFactory.to_dict(received_event)))
        new_event.source = common_event_properties['source']
        new_event.id = str(uuid.uuid4())

        send_message_fn(PlaigroundEventFactory.to_dict(new_event))


def _get_agent_key(game_id: str, role_id: str) -> str:
    digest = md5(
        bytes(game_id + role_id, "utf-8"),
        usedforsecurity=False
    ).digest()
    return str(uuid.UUID(bytes=digest[:16], version=3))


if __name__ == '__main__':
    # Set environment variables from .env files
    agent_directory_path = Path(os.path.dirname(__file__))

    load_dotenv(agent_directory_path / ".env")
    load_dotenv(agent_directory_path / ".env.local", override=True)

    agent_service_id = get_env_var("AGENT_SERVICE_ID")
    agent_service_role_id = get_env_var("AGENT_SERVICE_ROLE_ID")
    taskdata_container_url = get_env_var("TASKDATA_CONTAINER_URL")
    tournament_id = get_env_var("LOCAL_SERVER_TOURNAMENT_ID")
    task_id = get_env_var("LOCAL_SERVER_TASK_ID")

    _LOGGER.info(f"agent_service_id: {agent_service_id}")
    _LOGGER.info(f"agent_service_role_id: {agent_service_role_id}")
    _LOGGER.info(f"taskdata_container_url: {taskdata_container_url}")
    _LOGGER.info(
        f"Running local server test using tournament_id: {tournament_id}, task id: {task_id}")

    # Run the main function inside a simulation.
    # The simulator will create new threads for the server and the client
    # thread will execute the function run_agent_service
    LocalConnectionSimulator().run_simulation(
        run_agent_fn=run_agent_service,
        agent_service_id=agent_service_id,
        agent_service_role_id=agent_service_role_id,
        taskdata_container_url=taskdata_container_url,
        tournament_id=tournament_id,
        task_id=task_id,
        process_message_fn=process_message,
    )
