import argparse
import base64
import datetime
import gzip
import json
import logging
from typing import Dict, List, Optional, TypedDict

import requests

_LOGGER = logging.getLogger(__name__)

_BASE_BLOB_URL = "https://plaiground4bawstorage.blob.core.windows.net:443"
_TASKDATA_CONTAINER_URL = f"{_BASE_BLOB_URL}/taskdata"
_GAMEDATA_CONTAINER_URL = f"{_BASE_BLOB_URL}/gamedata"


#####################################################################################
# Exceptions

class MalformedConfirmationCodeException(Exception):
    """
    Raised when we try to parse a confirmation code which is malformed.
    """


class BlobDataDownloadException(Exception):
    """
    Raised when we fail to download task data from the blob.
    """

#####################################################################################
# Data classes & types


class Location(TypedDict):
    x: int
    y: int
    z: int
    pitch: int
    yaw: int


class MovementRegion(TypedDict):
    origin: Location
    size: Location


class PlayerState(TypedDict):
    movementRegion: Optional[MovementRegion]
    spawnLocation: Location


class BlockInfo(TypedDict):
    type: int


class WorldState(TypedDict):
    blobkChanges: Dict[str, BlockInfo]
    generatorName: str


class InitialGameState(TypedDict):
    instructions: str
    playerStates: Dict[str, PlayerState]
    worldState: WorldState


class TargetGameChanges(TypedDict):
    worldChanges: WorldState


class BlobTaskData(TypedDict):
    initialGameState: InitialGameState
    targetGameChanges: List[TargetGameChanges]


class BaseEventData(TypedDict, total=False):
    eventType: str
    id: str
    gameId: str
    taskId: str
    tournamentId: str
    producedAtDatetime: datetime.datetime
    source: str
    roleId: Optional[str]
    groupId: Optional[str]
    agentSubscriptionFilterValue: Optional[str]


class BlobGameEvents(TypedDict):
    game_id: str
    events: List[BaseEventData]


class CompletionCodeData(TypedDict):
    tournament_id: str
    challenge_type: str
    challenge_id: str
    task_id: str
    game_id: str
    role_id: str
    agent_service_id: str


class GameResults(TypedDict):
    gameEvents: List[BaseEventData]
    taskData: BlobTaskData
    completionCodeData: CompletionCodeData


#####################################################################################
# public methods

def get_game_data_for_completion_code(completion_code: str) -> GameResults:
    """
    Given a confirmation code, it returns a GameResults instance with all the data for that game.
    """
    completion_code_data = _parse_completion_code(completion_code)

    task_data = _download_task_data(completion_code_data["task_id"])
    game_data = _download_game_data(completion_code_data)

    return GameResults(
        gameEvents=game_data["events"],
        taskData=task_data,
        completionCodeData=completion_code_data
    )

#####################################################################################
# private methods


def _parse_completion_code(completion_code: str) -> CompletionCodeData:
    """
    Parse the completion code into it's separate IDs.

    The completion code is expected to be GZipped and base64-encoded. 

    Once decoded, the format of the completion code is as follows:

        {tournament_id}:{challenge_type}:{challenge_id}:{task_id}:{game_id}:{role_id}:{agent_service_id}

    Where all fields are GUIDs except for `challenge type` which is either `ac`
    or `hc`, depending on whether it's an `agent challenge` or `human
    challenge`.
    """
    _LOGGER.debug(f"Parsing completion code: {completion_code}")

    code_bytes = base64.b64decode(completion_code)
    completion_code = gzip.decompress(code_bytes).decode("utf-8")

    components = completion_code.split(':')

    if components[2] == 'hc':
        # human challenge are not supported. The code in this script assumes
        # that the game we're working with is from an agent challenge
        raise NotImplementedError("Human challenge completion codes are not supported yet")

    if len(components) != 7:
        raise MalformedConfirmationCodeException(
            f"Completion code '{completion_code}' is malformed. "
            f"Expected 7 components, got {len(components)}")

    (tournament_id,
     challenge_type,
     challenge_id,
     task_id,
     game_id,
     role_id,
     agent_service_id) = components

    return CompletionCodeData(
        tournament_id=tournament_id,
        challenge_type=challenge_type,
        challenge_id=challenge_id,
        task_id=task_id,
        game_id=game_id,
        role_id=role_id,
        agent_service_id=agent_service_id
    )


def _download_task_data(task_id: str) -> BlobTaskData:
    _LOGGER.debug(f"Downloading data for task: {task_id}")

    base_url = f'{_TASKDATA_CONTAINER_URL}/{task_id}'

    def _get_json(url: str) -> dict:
        _LOGGER.debug(f"Downloading data from: {url}")
        response = requests.get(url)
        if response.status_code != 200:
            raise BlobDataDownloadException(
                f"Failed to download data from {url}. "
                f"Status code: {response.status_code}")
        return response.json()

    task_data: BlobTaskData = {
        "initialGameState": InitialGameState(**_get_json(f'{base_url}/initialGameState.json')),
        "targetGameChanges": [TargetGameChanges(**t) for t in _get_json(f'{base_url}/targetGameChanges.json')],
        # "initialWorldComplete": f'{base_url}/initialWorldCompleteBlocks.json',
    }

    return task_data


def _download_game_data(completion_data: CompletionCodeData):
    """
    Download the game data from the blob storage.
    """

    _LOGGER.debug(f"Downloading game data for game id: {completion_data['game_id']}")

    group_id_components = [
        completion_data["tournament_id"],
        completion_data["challenge_type"],
        completion_data["challenge_id"],
    ]

    group_id = ':'.join(group_id_components)

    game_data_url = (
        f'{_GAMEDATA_CONTAINER_URL}/tournaments/{completion_data["tournament_id"]}'
        f'/groupId/{group_id}'
        f'/tasks/{completion_data["task_id"]}'
        f'/games/{completion_data["game_id"]}.json'
    )

    data = requests.get(game_data_url).json()

    return BlobGameEvents(game_id=data['id'],
                          events=data['events'])

#####################################################################################
# add main logic in case a user wants to run this as a standalone script


if __name__ == "__main__":
    """
    Some example test confirmation codes:

    H4sIAAAAAAAA/xWN2RFEIQgEI5oqRUB82XBI/iGs+z093WnOMiUx+TJ4ZMH3TESMSK022/fz/GqcJSyFLS5gvgo/gyHL1/JqoXU/rtEkNpBBB09IODcDi910nodO/ubNGuGMMV2faSlOJcHGFLPaaW1fsVClNtLkgvUEjGljhMmMSu5cn+npQ9RQ4sSbN3xavE+SrtKzYz9oeff950aBSw3WRaC4ycnMe58fJDmVawUBAAA=
    """

    parser = argparse.ArgumentParser(
        prog="download_game_data_from_completion_code",
        description=("Download game data associated with a completion code and prints it to "
                     "stdout as JSON object.")
    )

    parser.add_argument("--loglevel", default="INFO", help="Set the log level")
    parser.add_argument("completion_code", help="The completion code to download the game data for. Is is expected to be GZipped "
                        "and base64-encoded.")

    args = parser.parse_args()

    _LOGGER.setLevel(args.loglevel)
    completion_code = args.completion_code

    game_results = get_game_data_for_completion_code(completion_code)

    print(json.dumps(game_results, indent=4, sort_keys=True, default=str))
