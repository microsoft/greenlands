import logging
import os
from pathlib import Path

from dotenv import load_dotenv

from examples.tutorial.agents.command_agent import ChatCommandAgent
from examples.tutorial.environments.command_game_environment import (
        CommandGameEnvironment, CommandLocalGameState)
from agent_toolkit import (AgentToolkit, CommonEventsProperties, EventCallbackProvider,
                                      GreenlandsMessageClient, get_env_var, logger)
from agent_toolkit.wrappers.remote_task_loader import RemoteTaskLoader

_LOGGER = logger.get_logger(__name__)

logger_blocklist = [
    'uamqp',
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

    chat_command_agent = ChatCommandAgent(
        agent_service_id=agent_service_id,
        agent_service_role_id=agent_service_role_id,
    )

    def create_game_environment(
        callback_provider: EventCallbackProvider,
        common_events_properties: CommonEventsProperties
    ):
        env = CommandGameEnvironment(
            role_id=common_events_properties.role_id,
            callback_provider=callback_provider,
            initial_game_state=CommandLocalGameState()
        )

        env = RemoteTaskLoader(
            env,
            common_events_properties.task_id,
            taskdata_container_url)

        return env

    at = AgentToolkit(
        agent_service_id=agent_service_id,
        agent_service_role_id=agent_service_role_id,
        agent=chat_command_agent,
        create_game_environment_fn=create_game_environment,
        client=greenlands_message_client,
        max_games=2,
        auto_rejoin_agent_queue=False
    )

    try:
        at.run()
    except KeyboardInterrupt:
        _LOGGER.info(f"Shutting down client process {os.getpid()}")
