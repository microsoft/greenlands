import os
import signal
from queue import Queue
from threading import Thread
from typing import Callable

from agent_toolkit import BaseMessageClient, logger
from examples.local_server_simulator.client import LocalQueueClient
from examples.local_server_simulator.server import LocalQueueServer

_LOGGER = logger.get_logger(__name__)


class LocalConnectionSimulator:
    """Starts a LocalQueueServer and LocalQueueClient in separate processes.

    This class allows to test out environment functions with a local server
    based on multiprocessing Queues. The Simulator starts a LocalQueueServer
    in a separate process listening to a serverbound queue and writing into
    a clientbound queue.
    """

    def run_simulation(
        self,
        run_agent_fn: Callable[[BaseMessageClient, str, str, str], None],
        agent_service_id: str,
        agent_service_role_id: str,
        taskdata_container_url: str,
        tournament_id: str,
        task_id: str,
        process_message_fn: Callable[[dict, Callable, dict], None]
    ) -> None:
        """Starts the simulator and executes the given function.

        Args:
            run_agent_fn (callable): function that receives a GreenlandsClient
                or subclass instance and executes the behaviour of the agent.
                This function will be called with an instance of
                LocalQueueClient and will connect the environment and agent
                with a LocalQueueServer.
        """
        self.handle_termination_signals()

        # Create multiprocessing queue to communicate sever and agent
        # NOTE: adding explicitly types here so that mypy doesn't complain
        serverbound_queue: Queue = Queue()
        clientbound_queue: Queue = Queue()

        # Start server process
        server = LocalQueueServer(
            serverbound_queue,
            clientbound_queue,
            tournament_id,
            task_id,
            agent_service_role_id,
            process_message_fn,
        )

        server_thread = Thread(target=server.run)
        server_thread.name = "Simulator_ServerThread"
        server_thread.start()

        # Start client process
        client = LocalQueueClient(agent_service_id, serverbound_queue, clientbound_queue)
        client_thread = Thread(target=run_agent_fn, args=(
            client, agent_service_id, agent_service_role_id, taskdata_container_url))
        client_thread.name = "Simulator_ClientThread"
        client_thread.start()

        # Wait for child process
        server_thread.join()
        client_thread.join()

    @staticmethod
    def handle_termination_signals():
        """Overwrites the signal handler for SIGINT and SIGTERM to do nothing.
        """

        def exit_signal_handler(signum, frame):
            _LOGGER.info(f"Exiting Simulator program {os.getpid()}")
            exit()

        signal.signal(signal.SIGINT, exit_signal_handler)
        signal.signal(signal.SIGTERM, exit_signal_handler)
