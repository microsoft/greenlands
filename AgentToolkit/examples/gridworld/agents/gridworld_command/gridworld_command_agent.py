import re
from typing import Dict

from plaiground_agent_toolkit import Agent, LocalGameState, logger
from plaiground_agent_toolkit.event_aggregator.local_game_state import ConversationActivity

_LOGGER = logger.get_logger(__name__)
_LOGGER.setLevel('DEBUG')


class GridworldCommandAgent(Agent):
    """
    This agent will always return a random action from the pool of known actions.
    """

    def __init__(self, agent_service_id):
        super().__init__(agent_service_id)

        # From World.py
        # 0 noop; 1 forward; 2 back; 3 left; 4 right; 5 jump; 6-11 hotbar; 12 camera left;
        # 13 camera right; 14 camera up; 15 camera down; 16 attack; 17 use;

        self.action_map = {k: idx for (idx, k) in enumerate([
            "noop", "forward", "back", "left", "right", "jump", "hotbar-1", "hotbar-2", "hotbar-3",
            "hotbar-4", "hotbar-5", "hotbar-6", "camera left", "camera right", "camera up",
            "camera down",
            "attack", "use", "end_turn"
        ])}

    def next_action(self, observation: Dict, current_game_state: LocalGameState) -> int:
        next_command_index: int = observation["next_command_index"]
        was_last_action_end_turn: bool = observation["was_last_action_end_turn"]

        # end turn if there are no more messages to process
        latest_msg_idx: int = len(current_game_state.conversation_history) - 1
        if next_command_index > latest_msg_idx:
            if was_last_action_end_turn:
                return self.action_map['noop']
            else:
                return self.action_map['end_turn']

        # don't miss any unprocessed events
        most_recent_chat_message = current_game_state.conversation_history[
            next_command_index
        ]

        lower_message = most_recent_chat_message.message.lower()

        # documentation events. Useful from grouping different events visually in the log
        if lower_message.startswith("-----"):
            _LOGGER.info(f"Starting new section: {lower_message}")
            return self.action_map['noop']

        def _append_n_new_message(n: int, message: str) -> None:
            for _ in range(n):
                current_game_state.conversation_history.append(
                    ConversationActivity(
                        role_id=most_recent_chat_message.role_id,
                        datetime=most_recent_chat_message.datetime,
                        message=message
                    )
                )

        # process 'repeat' commands. They're just used to add more commands to the chat history
        # a repeat command has the following shape
        #     repeat 10 "move left"
        #       ^ will add 10 "move left" commands to the chat history.
        p = re.compile(r".*?repeat (\d+) [“”\"'](.+)['\"“”]$")
        if match := p.match(lower_message):
            num = int(match.group(1))
            command = match.group(2)

            _append_n_new_message(num, command)

            # once processed, we just return noAction for the current "next action" invocation
            return self.action_map['noop']

        # "macro" commands are used to do a sequence of actions that have a result. Similar to the
        # repeat system above, they append multiple events to the chat history to get the desired
        # effect.
        if "macro::" in lower_message:
            if "move_in_circle" in lower_message:
                acc_angle = 0
                while acc_angle < 360:
                    _append_n_new_message(1, "move forward")
                    _append_n_new_message(1, "look right")
                    acc_angle += 5

            elif "move_in_spiral" in lower_message:
                acc_angle = 0
                step_size = 1.
                while acc_angle < 360 * 2:  # 2 rotations
                    _append_n_new_message(int(step_size), "move forward")
                    _append_n_new_message(1, "look right")
                    acc_angle += 3
                    step_size += 0.4

            elif "move_in_square" in lower_message:
                for _ in range(4):
                    _append_n_new_message(50, "move forward")
                    _append_n_new_message(90 // 5, "look right")

            elif "look_neutral" in lower_message:
                _append_n_new_message(180 // 5, "look up")  # ensure we're looking all the way up
                _append_n_new_message(90 // 5, "look down")  # look forward

            elif "place_cross" in lower_message:
                _append_n_new_message(6, "look down")
                for i in range(1, 5):
                    _append_n_new_message(1, f"hotbar-{i}")
                    _append_n_new_message(90 // 5, "look right")

            elif "place_different_colors" in lower_message:
                _append_n_new_message(6, "look down")
                for hotbar_idx in range(1, 7):
                    _append_n_new_message(1, f"hotbar-{hotbar_idx}")
                    _append_n_new_message(1, "block break")

            return self.action_map['noop']

        # handle low level actions
        if "move" in lower_message:
            _LOGGER.info(f"Agent executing move command: {lower_message}")

            if "forward" in lower_message:
                return self.action_map["forward"]
            elif "back" in lower_message:
                return self.action_map["back"]
            elif "left" in lower_message:
                return self.action_map["left"]
            elif "right" in lower_message:
                return self.action_map["right"]
            else:
                # default to forward
                return self.action_map["noop"]

        elif "jump" in lower_message:
            _LOGGER.info(f"Agent executing jump command: {lower_message}")
            return self.action_map["jump"]

        elif "block break" in lower_message:
            _LOGGER.info(f"Agent executing attack command: {lower_message}")
            return self.action_map["attack"]

        elif "block place" in lower_message:
            _LOGGER.info(f"Agent executing use command: {lower_message}")
            return self.action_map["use"]

        elif "hotbar" in lower_message:
            _LOGGER.info(f"Got hotbar command: {lower_message}")

            for i in range(1, 7):
                if f"hotbar-{i}" in lower_message:
                    return self.action_map[f"hotbar-{i}"]

            _LOGGER.warning(f"Could not parse hotbar command: {lower_message}")
            return self.action_map["noop"]

        elif "look" in lower_message:
            _LOGGER.info(f"Agent executing rotate command: {lower_message}")

            """
            In Minecraft:
            =============

            YAW:
            - Positive Z is YAW == 0
            - Positive X is YAW == 90
            - Negative Z is YAW == 180
            - Negative X is YAW == 270

            Seen from the top (-Y direction)

            ┍──────── positive Z
            │
            │
            │
            positive X

            PITCH:
            - -90 is straight UP
            - 0 is straight ahead
            - 90 is straight DOWN


            In Gridworld:
            =============

            YAW (rotated 180 degrees with respect to Minecraft):
            - Negative Z is YAW == 0
            - Positive X is YAW == 90
            - Positive Z is YAW == 180
            - Negative X is YAW == 270

                           positive X
                               │
                               │
                               │
            positive Z ────────┛

            PITCH (inverted with respect to Minecraft):
            - -90 is straight DOWN
            - 0 is straight ahead
            - 90 is straight UP
            """

            # camera directions are inverted (3rd person)
            if "left" in lower_message:
                # decrease YAW
                return self.action_map["camera left"]
            elif "right" in lower_message:
                # increase YAW
                return self.action_map["camera right"]
            elif "up" in lower_message:
                # increase PITCH
                return self.action_map["camera down"]
            elif "down" in lower_message:
                # decrease PITCH
                return self.action_map["camera up"]
            else:
                # default to left
                return self.action_map["noop"]

        else:
            _LOGGER.warning(f"Got unknown command: {lower_message}")
            return self.action_map["noop"]
