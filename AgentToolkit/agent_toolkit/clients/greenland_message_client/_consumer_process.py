import asyncio
from multiprocessing import Process, Queue
from typing import Optional

from azure.eventhub import EventData
from azure.eventhub.aio import EventHubConsumerClient, PartitionContext

from agent_toolkit import logger

_LOGGER = logger.get_logger(__name__)


class GreenlandMessageConsumer(Process):
    def __init__(
            self,
            queue: Queue,
            publish_subscribe_connection_string: str,
            consumer_group: str,
            event_hub_name: str,
    ):
        super().__init__(
            name="GreenlandMessageConsumer_Process",
        )

        self.__event_queue: Queue[EventData] = queue

        self.consumer = EventHubConsumerClient.from_connection_string(
            conn_str=publish_subscribe_connection_string,
            consumer_group=consumer_group,
            eventhub_name=event_hub_name,
        )

        self.event_loop = asyncio.new_event_loop()

    async def _subscribe_async(self) -> None:
        # https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-python-get-started-send#create-a-python-script-to-receive-events
        async def process_raw_event(partition_context: PartitionContext,
                                    event: Optional[EventData]) -> None:
            if event is None:
                _LOGGER.warning(f"Subscription returned None event. This should not happen.")
                return

            self.__event_queue.put(event)

        while True:
            async with self.consumer:
                # https://docs.microsoft.com/en-us/python/api/azure-eventhub/azure.eventhub.eventhubconsumerclient?view=azure-python#azure-eventhub-eventhubconsumerclient-receive
                await self.consumer.receive(
                    on_event=process_raw_event,
                    max_wait_time=None,
                    starting_position="@latest"
                )

            _LOGGER.error(
                f"Subscription terminated prematurely. Subscription should be maintained until agent is explicitly terminated.")

    def run(self) -> None:
        self.event_loop.run_until_complete(self._subscribe_async())
