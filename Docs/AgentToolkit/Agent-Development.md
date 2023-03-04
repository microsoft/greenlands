# Agent Development

In the directory `examples/tutorial` you can find two basic agents and environments that show how the different components work. Not all classes need to be extended in order to create a new agent, and we encourage developers to limit the modifications to only including the agent's policy and the game state representation.


### Definitions

As all reinforcement learning problem, the first step is to define the action and observation space. These correspond to the input and output of the agent, respectively. With this in mind, you complete or extend the following classes:

* `LocalGameState` represents what the Agent Toolkit thinks is the current state of a game. It should contain all necessary information to produce the observation space. For example, if the agent now expects the turn number in the game, it is necessary to add a new attribute in the GameState to keep track of that. See the CommandLocalGameState for a possible implementation.
* `EventAggregator` is a wrapper on top of the GameState that defines how the game state changes for each of the possible events received from the server. If new events are added, this class needs to be extended.
* `GameEnvironment` defines the interaction between the game state and the agent actions.
  * `step` updates the game state after applying the action and returns the new observation.
  * `to_observation_space` retrieves the game state in the specific format expected by the agent.
Additionally, GameEnvironments can be integrated like wrappers that extend some of its methods.
* `EventCallbackProvider` provides methods to call when an operation that may trigger an event has happened. When a method is called, the event provider translates the operation into the corresponding Event that can be sent to the Minecraft server.
* `Agent` is a wrapper around the logic of assigning actions to every observation space. It can be implemented with any type of function or model, as long as it complies with the class API.


### Agent set up

When the agent is implemented, it's time to connect it to the Minecraft server. See `run_agent_service.py` scripts for specific examples of the following steps. You must first register the agent through the service and obtain the necessary access keys for EventHub and Azure Storage. Use them to set up a `.env` file.

The `AgentToolkit` class will orchestrate the traffic of events. You need to create:
* The client that will send and receive the Events from the source. `GreenlandMessageClient` is the class that interacts with EventHub.
* The environment generator function `create_game_environment`. This function combines all the environments and wrappers and returns the outer instance.

When you call AgentToolkit `run` method, the connection to client is open and the process stays in a loop listening for events. See more details about the interaction between the AgentToolkit and the server in the [Getting Started](/AgentToolkit/Getting-Started.md) section.


### Task loading

The tasks are available to the Minecraft server via Azure container, they are read directly from blobstorage.

Each task is expected to be stored in three files and follow an strict json format. The structure of the container must be:

<container_name>/<task_id>/initialWorldCompleteBlocks.json
<container_name>/<task_id>/initialGameState.json
<container_name>/<task_id>/targetGameChanges.json

The id of the task must be the one given by the Service. The URL of the container should be used to set the environment variable `TASKDATA_CONTAINER_URL` when running the agent service.


## Agent testing

When you develop a new Agent, and possibly a new GameEnvironment, it's important
that you test if they work correctly with the AgentToolkit. Plaiground supports
several test scenarios:
* Automatic local tests using a unittest battery.
* Manual local simulation using a server and client simulator that allows you to
connect two threads and exchange Events, but using local queues that mock the interactions
with EventHub. You can program the server to emit events in a custom order. In the directory `examples/tests` you can find a
battery of tests used for integration tests of the Agent Toolkit and the agents defined in
`examples/tutorial/agents` and `examples/tutorial/environments`.

* Manual remote tests, where you connect your agent to the Plaiground Minecraft server through
EventHub and can play against your agent in real games. In the current implementation, there are no automatic checks for this instance, you'll
need to manually review the logs to ensure the system works as expected.


## Adding new events

Events are what the Agent Toolkit uses to communicate with the rest of the PlaiGround platform. The event models themselves
are generated as part of the `PythonClient` that gets generated from Service (see Service's own documentation for more information
about this process).

When adding a new event to Service, the _PythonClient_ will have classes for the new models. But if you want to work with them then
there are some things you need to do if you want to work with these classes from with AT:

1. Register the new event classes in `AgentToolkit/agent_toolkit/event_factory.py` `_REGISTERED_EVENTS` types list.
    - This is so that AT's type checker (`mypy`) is able to know about these new events and can properly type-check your usage of them
1. If you want to be able to receive and aggregate the specific event into a `GameState` then you also need to add the new event to the
   list at the top of `AgentToolkit/agent_toolkit/event_factory.py`