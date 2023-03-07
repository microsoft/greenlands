import threading
from queue import Empty, Queue
from threading import Event as ThreadingSyncEvent
from typing import Any, Dict, Optional, Protocol, Tuple

from greenlands_client.model.platform_game_end_event import PlatformGameEndEvent
from greenlands_client.model.platform_player_turn_change_event import PlatformPlayerTurnChangeEvent

from agent_toolkit import (Agent, BaseMessageClient, CommonEventsProperties,
                                      EventCallbackProvider, GameEnvironment, RegisteredEvent,
                                      logger)

_LOGGER = logger.get_logger(__name__)


class CreateGameEnvironmentFnType(Protocol):
    # https://mypy.readthedocs.io/en/stable/protocols.html#callback-protocols
    def __call__(
        self,
        common_events_properties: CommonEventsProperties,
        callback_provider: EventCallbackProvider
    ) -> GameEnvironment:
        ...


class GameThread(threading.Thread):
    """
    This class is the top-level container of a game. It is in charge of managing the GameEnvironment
    instance for this game, handing it events that come from event hub, and frequently calling the
    step function on the GameEnvironment.
    """

    def __init__(
        self,
        common_events_properties: CommonEventsProperties,
        create_game_environment_fn: CreateGameEnvironmentFnType,
        agent: Agent,
        client: BaseMessageClient,
        wait_for_game_environment: bool = False,
    ) -> None:
        """
        Initializes the game thread.
        """

        super().__init__()

        self.name = f"GameThread-{common_events_properties.game_id}"

        _LOGGER.info(
            f"Initializing game thread for game {common_events_properties.game_id} "
            f"role {common_events_properties.role_id}"
        )

        self.__event_queue: Queue[RegisteredEvent] = Queue()

        self.agent = agent
        self.client = client
        self.common_events_properties = common_events_properties
        self.wait_for_game_environment = wait_for_game_environment

        self.callback_provider = EventCallbackProvider(
            message_client=client,
            common_events_properties=common_events_properties,
        )

        self.create_game_environment_fn = create_game_environment_fn

        self.running = True

    def stop(self) -> None:
        """
        Gracefully stops the current thread's execution
        """
        self.running = False

    def enqueue_event(self, event: RegisteredEvent) -> None:
        """Tells the current GameThread that a new Event for its game was received.

        This method is meant to be the main communication channel between the
        game thread and the main thread where Events are being received.

        Args:
            event (RegisteredEvent): The event that has been received by the caller
                from an instance of BaseMessageClient or subclass.
        """
        if not self.wait_for_game_environment:
            # WARNING this event will be processed in a different Thread, meaning that
            # for the rest of the execution of the current Thread you can't assume
            # the event has finished processing.
            self.__event_queue.put((event, None), block=False)
        else:
            sync_event = ThreadingSyncEvent()
            # WARNING this call is blocking until the receiver of the data calls sync_event.set()
            # TODO what is the effect of the block=False parameter?
            self.__event_queue.put((event, sync_event), block=False)
            sync_event.wait()

    def _poll_event_queue(self) -> Tuple[Optional[RegisteredEvent], Optional[ThreadingSyncEvent]]:
        """Queries the event queue without blocking.

        The head of the queue must be a tuple. If self.wait_for_game_environment is True,
        the second element of the tuple is a threading event that can be used by
        the caller for synchronization.

        If self.wait_for_game_environment is False, the second element of the tuple should
        be None.

        If the queue is empty, the function returns (None, None)

        Returns:
            Tuple[Optional[RegisteredEvent], Optional[ThreadingSyncEvent]]: The head of the Queue.
                If the queue is empty, the function returns (None, None)
        """
        try:
            head = self.__event_queue.get(block=False)
            return head
        except Empty:
            return None, None

    def _end_turn(self) -> None:
        """
        Updates local game state to reflect the end of the turn
        """
        _LOGGER.debug(f"Ending agent's turn for game {self.common_events_properties.game_id}")

        # update local game state
        self.environment.game_state.player_turn_change('')

    def run(self) -> None:
        # Contains a possible threading.Event that's blocking the event producer process
        # until sync_event.set() is called.
        sync_event = None

        # initialize game env resources directly inside the thread so that all required memory
        # directly belongs to the thread and doesn't need to be moved which could cause errors
        # in some cases (e.g. when initializing a renderer)
        self.environment: GameEnvironment = self.create_game_environment_fn(
            common_events_properties=self.common_events_properties,
            callback_provider=self.callback_provider,
        )

        # try to have the environment populate its own local information this
        # can throw an exception if the initial state cannot be "initialized"
        # (e.g. there's an error when downloading the game state from the
        # server)
        self.environment.reset()

        current_observation: Optional[Dict[str, Any]] = None
        is_first_step_in_turn = True

        while self.running:
            # get event from queue
            event, sync_event = self._poll_event_queue()
            if event is not None:
                # clean up stuff and end thread if we got a game end event
                if isinstance(event, PlatformGameEndEvent):
                    break  # exit game loop and end thread

                self.environment.apply_event(event)

                if (
                    isinstance(event, PlatformPlayerTurnChangeEvent)
                    and getattr(event, "previous_active_role_id", None) == self.common_events_properties.role_id
                ):
                    # If agent's turn was ended by Plugin then inform agent of
                    # episode end. This can happen in two cases:
                    # 1. The agent's turn was ended by the Plugin forcefully
                    #    (e.g. turn time limit was exceeded)
                    # 2. The agent itself ended its turn and Plugin is informing
                    #    us that this happened
                    self.agent.turn_end()

                    # update flag informing that next "step" will be the first
                    # in the next turn
                    is_first_step_in_turn = True

                if sync_event is None and self.wait_for_game_environment:
                    _LOGGER.warning(
                        "Event was queued without a threading.Event instance but " +
                        "wait_for_game_environment is True. This can possibly block the " +
                        "caller execution."
                    )

            is_agents_turn = (
                self.environment.game_state.active_role_id == self.common_events_properties.role_id
            )
            if is_agents_turn:

                # if this is the agent's first turn then populate the first
                # observation with the result of stepping with an empty noop
                # action.
                if is_first_step_in_turn:
                    self.environment.turn_is_starting()

                    # We want to "refresh" current_observation before the first
                    # step of agent's turn starts. This prevents us from having
                    # the agent start with an empty observation that was
                    # populated when the game was created OR having the
                    # observation miss out any events that happened since the
                    # agent's last turn end.
                    current_observation, _, _, _ = self.environment.step(self.environment.no_op_action)

                    is_first_step_in_turn = False
                    self.environment.turn_in_progress()

                try:
                    action = self.agent._thread_safe_next_action(  # noqa
                        current_observation,
                        self.environment.game_state
                    )

                except Exception as err:
                    # try to restart agent if next_action raised an exception,
                    # and retry in the next loop
                    _LOGGER.exception(
                        "Restarting agent since it crashed with exception: %s", err)

                    self.agent._thread_safe_restart(err)

                else:
                    # take step if agent next action didn't raise an exception

                    # new observation, reward, done, info
                    current_observation, _, done, _ = self.environment.step(action)
                    self.environment.turn_in_progress()

                    if done:
                        is_first_step_in_turn = True
                        self.environment.turn_is_ending()
                        self._end_turn()

            if sync_event is not None:
                # Not calling sync_event.set() here will block the execution of the producer Thread.
                # It is waiting for the confirmation that the GameEnvironment finished the execution
                #  of apply_event and step methods.
                sync_event.set()
                sync_event = None

        # if we get here then it means a GameEnd event was received, or we were asked to stop
        # gracefully
        _LOGGER.info(f"Closing game thread for game {self.common_events_properties.game_id}")
        if sync_event is not None:  # Unblock the producer in case it's waiting.
            sync_event.set()
            sync_event = None

        self.environment.close()

        self.agent.game_end()
