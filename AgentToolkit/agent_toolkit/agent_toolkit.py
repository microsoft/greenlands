"""Main module class.

The AgentToolkit handles the connection to AzureEventHub and multiple
concurrent games.
It handles the initial Events sent and received before the Agent is
completely included in the game through the PlatformPlayerJoinsGameEvent.
The AgentToolkit created new games, keeps track of active games and their
associated GameEnvironment instances, and distributes received events to
corresponding games based on game_id. It also sends back the events generated
by each GameEnvironment to the AzureEventHub.
"""
import uuid
from datetime import datetime
from typing import Dict

from plaiground_client.model import event_source
from plaiground_client.model.agent_is_ready_event import AgentIsReadyEvent
from plaiground_client.model.platform_game_end_event import PlatformGameEndEvent
from plaiground_client.model.platform_game_start_event import PlatformGameStartEvent

from agent_toolkit import BaseMessageClient, logger
from agent_toolkit.agent import Agent
from agent_toolkit.common_events_properties import CommonEventsProperties
from agent_toolkit.event_factory import RegisteredEvent
from agent_toolkit.game_thread import CreateGameEnvironmentFnType, GameThread

_LOGGER = logger.get_logger(__name__)


class AgentToolkitUnexpectedExit(Exception):
    """Indicates an AgentToolkit instance has finished its execution without user intervention.
    """
    pass


class AgentToolkit:
    """Handles the connection and distributes events for different games.
    """

    def __init__(
        self,
        agent_service_id: str,
        agent_service_role_id: str,
        agent: Agent,
        create_game_environment_fn: CreateGameEnvironmentFnType,
        client: BaseMessageClient,
        max_games: int = 3,
        auto_rejoin_agent_queue: bool = True,
        wait_for_game_environment: bool = False
    ) -> None:
        """Main class that handles all games for a single agent.

        TODO complete here:

        Args:
            agent_service_id (str): TODO complete description
            agent_service_role_id (str): TODO complete description
            agent (Agent): TODO complete description
            create_game_environment_fn (CreateGameEnvironmentFnType): TODO complete description
            client (BaseMessageClient): TODO complete description
            max_games (int, optional): TODO complete description. Defaults to 3.
            auto_rejoin_agent_queue (bool, optional): TODO complete description. Defaults to True.
            wait_for_game_environment (bool, optional): If True, the game thread will wait
                until the GameEnvironment has finished processing events and updating
                the simulation step. Defaults to False.
        """
        self.agent_service_id = agent_service_id
        self.agent_service_role_id = agent_service_role_id
        self.create_game_environment_fn = create_game_environment_fn
        self.agent = agent
        self.client = client
        self.max_games = max_games
        self.auto_rejoin_agent_queue = auto_rejoin_agent_queue
        self.wait_for_game_environment = wait_for_game_environment

        self._active_game_threads: Dict[str, GameThread] = {}

    def run(self) -> None:
        try:
            self._send_agent_ready_event(self.max_games)
            self._begin_processing_events()
        except KeyboardInterrupt as e:
            # kill all game threads if necessary
            for (game_id, game_thread) in self._active_game_threads.items():
                _LOGGER.debug(f"Stopping game thread for game {game_id}")

                game_thread.stop()
                game_thread.join()  # wait for thread to finish

            # move control up the stack
            raise e

        raise AgentToolkitUnexpectedExit(
            f"Agent.run() ended unexpectedly. Agent should run indefinitely "
            f"until manually stopped.")

    def _send_agent_ready_event(self, number_of_events: int) -> None:
        """
        Sends event to server indicating the Agent is ready to accept games.
        """
        # TODO it would be good to have some retry mechanism if the server
        # does not assign a game in a long time.

        agent_ready_buffer = []
        for _ in range(number_of_events):
            event = AgentIsReadyEvent(
                id=str(uuid.uuid4()),
                max_games=self.max_games,
                game_id='',
                task_id='',
                tournament_id='',
                agent_service_id=self.agent.agent_service_id,
                source=event_source.EventSource(value="AgentService"),
                produced_at_datetime=datetime.utcnow().isoformat() + 'Z'
            )

            agent_ready_buffer.append(event)

        self.client.send_events(agent_ready_buffer)

    def _begin_processing_events(self) -> None:
        self.client.subscribe(self._process_event)

    def _process_event(self, event: RegisteredEvent) -> None:
        if event is None:
            _LOGGER.error("Attempted to process a None value as event from event_producer. " +
                          "This should not be possible. Please contact a platform administrator!")
            return

        # Ignore all events which are NOT from the Minecraft Plugin. E.g. events from the current or other agents
        elif getattr(event, "source", None) != event_source.EventSource(value="MinecraftPlugin"):
            return

        elif not hasattr(event, "game_id"):
            _LOGGER.warning(f"Received event without game_id. Ignoring event.\nEvent:\n"
                            f"{event.event_type}, game_id: {event.game_id}")
            return

        elif event.agent_subscription_filter_value != self.agent_service_id:
            # Just ignore events that are not meant for this agent service
            return

        elif event.game_id in self._active_game_threads:
            game_thread = self._active_game_threads[event.game_id]

            # send event to game thread, so it can handle it as needed
            game_thread.enqueue_event(event)

            # if this is a game_end event then clean up game environment and remove game from
            # active games otherwise, pass event to game environment
            if isinstance(event, PlatformGameEndEvent):
                _LOGGER.debug(f"Game {event.game_id} finished, removing from active games")
                del self._active_game_threads[event.game_id]

                if self.auto_rejoin_agent_queue:
                    _LOGGER.debug(
                        f"Agent is configured to automatically rejoin the agent queue after game "
                        f"is finished. Sending AgentIsReadyEvent...")
                    self._send_agent_ready_event(1)

        elif isinstance(event, PlatformGameStartEvent):
            self._register_new_game(event)

        else:
            # if we get here then we know that the event is not a
            # PlatformPlayerJoinsGameEvent, and we're not tracking the game to which it
            # belongs, which should never happen
            _LOGGER.warning(
                f"Received event {event.__class__} for game that is not being tracked. This is likely an issue " +
                "with the Greenland platform itself, please report this issue to a platform " +
                f"administrator so they can investigate. "
                f"Game id: {event.game_id}, event: {event.id}")

    def _register_new_game(self, event: PlatformGameStartEvent):
        """Registers a new game in the AgentToolkit.

        Args:
            event: The PlatformGameStartEvent that was received.
        """

        if self.max_games == len(self._active_game_threads):
            _LOGGER.warning("Received a game start event but max games already reached! "
                            "This is likely a bug, please report it.")
            return

        _LOGGER.info(f"Creating Game environment for game {event.game_id}")

        # TODO: Add support to run each game in a separate process
        # Maybe create a new process here and connect through queues.

        common_events_properties = CommonEventsProperties(
            tournament_id=event.tournament_id,
            task_id=event.task_id,
            game_id=event.game_id,
            group_id=event.group_id,
            role_id=self.agent_service_role_id,
        )

        try:
            new_game_thread = GameThread(
                agent=self.agent,
                client=self.client,
                common_events_properties=common_events_properties,
                create_game_environment_fn=self.create_game_environment_fn,
                wait_for_game_environment=self.wait_for_game_environment,
            )

            # start thread and save it to active games
            new_game_thread.start()

            self._active_game_threads[common_events_properties.game_id] = new_game_thread

        except Exception:
            _LOGGER.exception(f"Error while setting initial game environment state "
                              f"for game: {event.game_id}, "
                              f"task: {event.task_id}, "
                              f"tournament: {event.tournament_id}")
