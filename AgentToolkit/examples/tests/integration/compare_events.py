import json
import argparse
from datetime import datetime
from pathlib import Path
from typing import List
from xmlrpc.client import boolean
from plaiground_agent_toolkit.event_factory import PlaigroundEventFactory


class RoleIdException(Exception):
    ...


class CompareException(Exception):
    ...


class EventError(dict):
    def __init__(
        self,
        received_event,
        sent_event,
        coordinate
    ) -> None:
        self["received_event"] = received_event
        self["sent_event"] = sent_event
        self["coordinate"] = coordinate


def read_data_file(file: str) -> str:
    filepath = Path(file)
    if not filepath.is_file():
        raise FileNotFoundError()

    with open(file, 'r') as all_events_file:
        all_events_str = all_events_file.read()
        if all_events_str == "":
            raise IOError("The file is empty")
        return all_events_str


def determine_role_ids(all_events: dict) -> dict:
    role_ids = {"human": "", "agent": ""}

    turn_change_events = [i for i, event in enumerate(all_events["receivedEvents"])
        if event["eventType"] == "PlatformPlayerTurnChangeEvent"
            and event["reason"] == "PLATFORM_GAME_START"]
    if len(turn_change_events) == 0:
        raise RoleIdException("PLATFORM_GAME_START PlatformPlayerTurnChangeEvent not found")
    elif len(turn_change_events) > 1:
        raise RoleIdException("To many PLATFORM_GAME_START PlatformPlayerTurnChangeEvents found, should only be one and " + len(turn_change_events).__str__() + " were found")

    role_ids["agent"] = all_events["receivedEvents"][turn_change_events[0]]["roleId"]
    role_ids["human"] = all_events["receivedEvents"][turn_change_events[0]]["nextActiveRoleId"]

    return role_ids


def get_move_indexes(all_events: dict, role_ids: dict) -> dict:
    move_indexes = { "receivedMoveIndexes": [], "sentMoveIndexes": [] }
    move_indexes["receivedMoveIndexes"] = [i for i,
        event in enumerate(all_events["receivedEvents"])
            if event["eventType"] == "PlayerMoveEvent" and event["roleId"] == role_ids["agent"]]
    move_indexes["sentMoveIndexes"] = [i for i,
        event in enumerate(all_events["sentEvents"])
            if event["eventType"] == "PlayerMoveEvent" and event["roleId"] == role_ids["agent"]]

    return move_indexes


def compare_moves(all_events: dict, move_indexes: dict) -> List[dict]:
    event_errors = []
    received_indexes = move_indexes["receivedMoveIndexes"]
    sent_indexes = move_indexes["sentMoveIndexes"]
    if len(received_indexes) != len(sent_indexes):
        raise CompareException("Different number of Player Moved Events sent and recieved")
    
    for i in range(0, len(received_indexes)):
        received_event = all_events["receivedEvents"][received_indexes[i]]
        sent_event = all_events["sentEvents"][sent_indexes[i]]

        coordinates = ["x", "y", "z"]
        for coord in coordinates:
            if received_event["newLocation"][coord] != sent_event["newLocation"][coord]:
                event_errors.append(EventError(received_event, sent_event, coord))
        
    return event_errors


def write_event_errors(event_errors) -> None:
    if len(event_errors) > 0:
        now = datetime.now()
        filename = 'compare_output_' + now.strftime('%m-%d-%y_%H-%M-%S') + '.json'
        with open(filename, 'w') as compareResultsFile:
            json.dump(event_errors, fp=compareResultsFile, indent=5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Compare sent and received move events created by running the static agent",
        usage="%(prog)s [options]"
        )
    parser.add_argument('--filepath', help="Sent and received events file created by running the static agent.", type=str)
    args = parser.parse_args()

    try:
        all_events_str = read_data_file(args.filepath)
        all_events = json.loads(all_events_str)
        roleIds = determine_role_ids(all_events)
        move_indexes = get_move_indexes(all_events, roleIds)
        event_errors = compare_moves(all_events, move_indexes)
        print("---------------------------------")
        print("Finished - Number of move errors: " + len(event_errors).__str__())
        print("---------------------------------")
        write_event_errors(event_errors)

    except FileNotFoundError:
        print("The file does not exist")
    except IOError as io:
        print(io)
    except RoleIdException as re:
        print(re)
    except CompareException as ce:
        print(ce)
