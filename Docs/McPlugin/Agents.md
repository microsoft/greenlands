
# McPlugin Agents

This page explains the key concepts behind the implementation of Agents in
McPlugin. Agents (implemented in class `AgentBot`) are entities that can perform
sequences of Actions according to the messages provided by the AgentService.

The class AgentBot is a wrapper over a
[Citizens2](https://github.com/CitizensDev/Citizens2) NPC. These NPC are
registed by the server as Players, but can be differentiated by checking
`npc.getEntity().hasMetadata("NPC")`.


## How actions are executed

An Action is a procedure than can involve several animations performed by the
Agent, that cannot be concurrent with the next action (so, they need to be
performed in sequence). For example, walking from location A to B is an Action.
While the Agent is moving, it can't be doing something else.

A single message from the AgentService can be translated into several Actions to
be performed sequentially. For example, placing a block can be implemented with
3 different Actions:

1. Walking to the location
2. Putting block to place in hand
3. Executing arm movement and making block appear in world.

Processes that do not repeat over time can be grouped in the same Action.

The Agent maintains a queue of pending Actions and runs them in order. The first
action is dequeued and scheduled with `ActionScheduler.schedule()`, using an
`ActionCallback` to notify the agent of Action end and its final state. This
will internally create a Runnable task that calls method `Action.execute()`
with a given tick frequency, until `Action.getState().hasFinished()` return
true. The method `AgentBot.maybeScheduleNextAction()` is called when new actions
are enqueued and in `ActionCallback.onActionEnd()`, but it can be periodically
invoked as well.


### How to define new Synchronous Actions

When the operations to perform are simple and you immediately know the end
status of the Action, there is no need to call a periodic task. To implement
such Action, put the operations in the `Action.setUp()` method and call
`transitionToState()` to either `ActionStatus.SUCCESS` or
`ActionStatus.FAILURE`. This tells the `ActionScheduler.schedule()` method the
function has finished and no further task needs to be run.

Method `Action.execute()` should perform no action.


### How to define new Asynchronous Actions

When you need to perform periodic operations until a certain criteria is
reached, you can define a method to execute every
`Action.getStateCheckIntervalTicks()` ticks. To implement such Action:
1. Execute all initial actions in the `Action.setUp()` method.
2. Execute one action step on method `Action.execute()` and and call
   `transitionToState()` to either `ActionStatus.SUCCESS` or
   `ActionStatus.FAILURE` if action is completed, i.e., needs no further calls.

For example, `MoveAction.setUp()` sets a target location for the entity
navigator, which internally starts the navigation. The method
`MoveAction.execute()` only checks whether the Citizens' navigator has stopped
and set the state to `ActionStatus.SUCCESS`. The action can be scheduled every
20 or more ticks, since it's reasonable to wait some ticks between the
navigation end and the execution of `ActionCallback.onActionEnd`, which triggers
the next action in queue.

On the other hand, `BreakBlockAction` is implemented using Citizens'
BlockBreaker class, which performs a single animation for hit and block damage.
The `BreakBlockAction.setUp()` method creates the block breaker instance. The
`BreakBlockAction.execute()` method executes the breaker's call (single hit) and
calculates whether the block has been destroyed or not. This Action must be
scheduled every server tick.
