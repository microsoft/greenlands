# How to collect data with Greenlands

This document contains a description of the process a Researcher must follow to collect interaction data using Greenlands.

## Prerequisites

Greenlands is composed of several components. All of them must be running before starting the data collection. More information on how to check this can be found in [Home.md](Home.md)

1. Ensure the Greenlands service server is running.
1. Check the Minecraft server IP works.

## High level view of the steps

Detailed descriptions of each step will be provided in the following sections.

1. Set up your Tournament and Challenges.
    1. Create and upload the Tasks that will be played in the Tournament.
    2. Create a Human Challenge for the Tournament by selecting a name and the tasks to be included.
    3. Create an Agent Challenge for the Tournament and select the Human Challenge created in the previous step. The tasks available in the selected Human Challenges become tasks considered in the Agent Challenge.
    4. Register the baseline or reference Agent for the Challenge. Once this is created, join commands can be distributed for human to agent data collection.

2. Start data collection. Distribute:
    1. The Minecraft server IP
    2. Generated join commands for Gamers to play against other Gamers in the created Human Challenges, or against Agents in the created Agent Challenges.

3. Download the data generated in the played games.


## Uploading tasks in json format

TODO: how to upload the tasks trough service?

When uploading a task as a .json file you may define all of properties of a Task in a single object.
Normally, creating a task requires multiple steps with manual input such creating task then editing target blocks through Minecraft. This format offers a way to more easily automate the process.

```jsonc
{
    "name": "Example Task Name",
    // Indicates if the task is Published or Draft. One a task has been published it cannot be edited.
    "state": "Published",
    "expectedCompletionDurationSeconds": 120,
    // These parameters are optional, if not present, they will be set to 0. They are not currently used.
    "worldSizeX": 160,
    "worldSizeZ": 160,
    // Add limits to all games played on the task. Can limit by time or by turns
    // If limits are not specified, the game will play indefinitely until a player ends it.
    "gameLimits": {
        "maxTimeOutSeconds": 600,
        "maxTurnLimit": 10
    },
    // Dictionary of role id to turn limits
    "turnLimits": {
        // Tournament Role ID
        "3f0f8db4-3b67-4c4f-a472-9a1f95e111ff": {
            "maxTimeOutSeconds": 60
        },
        "b20e0fed-001c-47e7-8e81-d6037a5c571f": {
            "maxTimeOutSeconds": 60
        }
    },
    "initialGameState": {
        "instructions": "Example instructions. Place 3 blue blocks",
        // State of Minecraft world
        "worldState": {
            // Minecraft worlds are created using generators that load new chunks as they are needed.
            "generatorName": "CleanroomGenerator:.1|minecraft:grass_block",
            // Contains a sparse representation of the blocks that are part of the initial game state and which are applied to the generated world.
            // Examples are partially constructed structures, landmarks like signs, or a letters on the floor indicating cardinal directions
            "blockChanges": {
                // [x, z, y, pitch, yaw] Values are absolute, meaning they are directly set in Minecraft without modifications
                "[-10.0,0.0,-3.0,0.0,0.0]": {
                    // ID of Minecraft Material
                    "type": 11
                },
            }
        },
        // Dictionary with role id to player state.
        "playerStates": {
            "9393f7ca-7841-44d4-8d32-67c5f898aa17": {
                "spawnLocation": {
                    "x": 1,
                    "z": 2,
                    "y": 3,
                    "pitch": 0,
                    "yaw": -180
                },
            },
            "1a92a312-e960-4eea-995a-7f6ecf570268": {
                "spawnLocation": {
                    "x": 1,
                    "z": 2,
                    "y": 3,
                    "pitch": 0,
                    "yaw": -180
                },
                "movementRegion": {
                    "origin": {
                        "x": 1,
                        "z": 2,
                        "y": 3,
                        "pitch": 0,
                        "yaw": 0
                    },
                    "size": {
                        "x": 15,
                        "z": 15,
                        "y": 100,
                        "pitch": 0,
                        "yaw": 0
                    }
                },
                // Optional dictionary, item id to count
                "inventory": {
                    "4": 2,
                    "12": 1,
                },
                // Optional block
                "currentItem": {
                    "type": 1
                }
            }
        }
    },
    // A sequence of target games changes. Note: This only stores the desired new state of changed items. Target States may be computed by applying the changes to the initial state.
    // If an item is not specified in the target change, such as playerChanges for role 1a92a312 in this example, then that property of state would not be considered when determining if current game state matches the target.
    "targetGameChanges": [
        {
            // Changes made to Minecraft world
            "worldChanges": {
                // Contains a sparse representation of the blocks which have changed from the previous state
                "blockChanges": {
                    // [x, z, y, pitch, yaw] Values are absolute, meaning they are directly set in Minecraft without modifications
                    "[-1.0,2.0,-1.0,0.0,0.0]": {
                        // ID of Minecraft Material
                        "type": 177
                    }
                }
            },
            // Changes made to Player state
            "playerChanges": {
                "9393f7ca-7841-44d4-8d32-67c5f898aa17": {
                    "inventoryChanges": {
                        "4": 2,
                        "12": 1,
                    },
                    // Optional block
                    "currentItem": {
                        "type": 1
                    }
                }
            }
        }
    ]
}
```

## Running an Agent Service

To be able to play against an agent, first you need to register the service in the Dashboard. Create a new agent service for each agent instance to run. For example, if you want to run 3 MHB agents instances, register 3 agent services. To do so, add any name to identify the agent, e.g. MHB1. Add any URL and check the agent challenge and Team are correct.

The agent needs to be run through a script that recovers these environment variables and creates an environment, a client to connect to Event Hub, and an instance of the `AgentToolkit` class. There are several examples included in the repository. Once the script is running, it will start exchanging messages with the Greenlands server and the agent will be queued for new games.

TODO: how to get the connection keys trough service?

## Pairing players with agents

Players are assigned to a game trough join codes, which contain information about the tournament, challenge ang task id, and optionally the agent id. A player must insert a join code in the Minecraft game to be assigned a particular task and agent service. When the agent service is available, the player will be teleported to the game world.

TODO how to get the join codes trough service?

## Completion codes
Once a game is finished, a completion code is produced and given to the player. This allows us to map the join code used with the game that was played. The instructions to generate and copy the completion code are shown in Minecraft.
The completion code is expected to be GZipped and base64-encoded. Once decoded, the format of the completion code is as follows:
`{tournament_id}:{challenge_type}:{challenge_id}:{task_id}:{game_id}:{role_id}:{agent_service_id}`
Where all fields are GUIDs except for `challenge type` which is either `ac` or `hc`, depending on whether it's an `agent challenge` or `human challenge`. With this information, game data can be retrieved following the example above.
Using the script `download_game_data_from_completion_code.py` and the completion code, a json with the task information and all the events generated on the game is downloaded.


## Download game data from Azure storage
Game data is stored in json files that can be found in an Azure container:
`<GAMEDATA_CONTAINER_URL>/tournaments/<tournament_id>/groupId/{group_id}/tasks/<task_id>/games/<game_id>.json`
It has the format

```
{
  "eventType": "PlatformPlayerJoinsGameEvent"
  "id": "cca2f8f3-6009-4965-9078-353d2afb25b6",
  "events": [
    # List of events encoded as json dictionaries. Each event has its unique format with
    # some common keys
    {
      "id": "34b75530-1e77-46ea-944b-1ca49890b8ed",
      "gameId": "cca2f8f3-6009-4965-9078-353d2afb25b6",
      "taskId": "d1b554d4-d6b6-48e0-a28c-3ef696be46de",
      "tournamentId": "ee11ab7d-ad66-4541-8dc7-5cdc0bc4c24b",
      "producedAtDatetime": "2022-11-16T17:09:12.916723009Z",
      "source": "MinecraftPlugin",
      "roleId": "1a92a312-e960-4eea-995a-7f6ecf570268",
      …
    }
]
```

* `eventType` is the class of the event, and indicates which fields will be present in the event.
* `id` is the identifier of the event
* `source` can be either `AgentService`, when the event was sent by the Agent Toolkit, or `MinecraftPlugin`, when the event was sent by Minecraft.
* `roleId` role of the player that causes the emission of the event. It can be human or agent. It is null for events like `PlatformGameStartEvent`.
Game data files only contain the events that happen in the world. To calculate the final world state, they must be sequentially applied to the starting game state. Therefore, the task data is also necessary. It can be also downloaded from url:
`<TASKDATA_CONTAINER_URL>/<task_id>`.
This blob contains the same information as the complete task json files that can uploaded trough dashboard, separated in 3 files:
* `initialGameState.json`, equivalent to the content of the same key in the complete task json file
* `targetGameChanges.json`, equivalent to the content of the same key in the complete task json file
* `initialWorldCompleteBlocks.json` ??
