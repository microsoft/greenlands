import logging
import os
from pathlib import Path

import numpy
from dotenv import load_dotenv

from examples.gridworld.agents.gridworld_command.gridworld_command_agent import GridworldCommandAgent
from examples.gridworld.agents.gridworld_command.gridworld_command_game_environment import \
    GridworldCommandGameEnvironment
from examples.gridworld.environment.task import Task
from examples.gridworld.environment.world import World
from examples.gridworld.environment.wrappers.iglu_format_task_converter import \
    IGLUFormatTaskConverterWrapper
from agent_toolkit import (AgentToolkit, CommonEventsProperties, EventCallbackProvider,
                                      GreenlandsMessageClient, get_env_var, logger)
from agent_toolkit.wrappers.remote_task_loader import RemoteTaskLoader

_LOGGER = logger.get_logger(__name__)

logger_blocklist = [
    'uamqp',
    'numba.core.byteflow'
]

for module in logger_blocklist:
    logging.getLogger(module).setLevel(logging.WARNING)

logging.getLogger('agent_toolkit').setLevel(logging.DEBUG)

if __name__ == "__main__":
    agent_directory_path = Path(os.path.dirname(__file__))

    load_dotenv(agent_directory_path/".env")
    load_dotenv(agent_directory_path/".env.local", override=True)

    agent_service_id = get_env_var("AGENT_SERVICE_ID")
    agent_service_role_id = get_env_var("AGENT_SERVICE_ROLE_ID")
    taskdata_container_url = get_env_var("TASKDATA_CONTAINER_URL")
    publish_subscribe_connection_string = get_env_var("PUBLISH_SUBSCRIBE_CONNECTION_STRING")
    event_hub_name = get_env_var("EVENT_HUB_NAME")
    event_hub_consumer_group = get_env_var("EVENT_HUB_CONSUMER_GROUP")

    _LOGGER.info(f"agent_service_id: {agent_service_id}")
    _LOGGER.info(f"agent_service_role_id: {agent_service_role_id}")
    _LOGGER.info(f"taskdata_container_url: {taskdata_container_url}")
    _LOGGER.info(f"event_hub_name: {event_hub_name}")
    _LOGGER.info(f"event_hub_consumer_group: {event_hub_consumer_group}")

    greenlands_message_client = GreenlandsMessageClient(
        agent_service_id=agent_service_id,
        publish_subscribe_connection_string=publish_subscribe_connection_string,
        event_hub_name=event_hub_name,
        consumer_group=event_hub_consumer_group,
    )

    random_action_agent = GridworldCommandAgent(
        agent_service_id=agent_service_id,
    )

    def create_game_environment(
        callback_provider: EventCallbackProvider,
        common_events_properties: CommonEventsProperties
    ):
        env = GridworldCommandGameEnvironment(
            role_id=common_events_properties.role_id,
            callback_provider=callback_provider,
            game_state=World(),
            select_and_place=True,
            discretize=True,
            max_steps=2050,
            target_in_obs=True,
            render=False,
            render_size=(512, 512)
        )

        custom_grid = numpy.ones((9, 11, 11))
        env.set_task(Task("", custom_grid))

        env = RemoteTaskLoader(
            env,
            common_events_properties.task_id,
            taskdata_container_url)

        env = IGLUFormatTaskConverterWrapper(env)

        return env

    at = AgentToolkit(
        agent_service_id=agent_service_id,
        agent_service_role_id=agent_service_role_id,
        agent=random_action_agent,
        create_game_environment_fn=create_game_environment,
        client=greenlands_message_client,
        max_games=14,
        auto_rejoin_agent_queue=True
    )

    try:
        at.run()
    except KeyboardInterrupt:
        _LOGGER.info(f"Shutting down client process {os.getpid()}")
