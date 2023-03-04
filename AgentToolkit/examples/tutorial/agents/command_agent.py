import re
from typing import Dict

from examples.tutorial.environments import action_space
from examples.tutorial.environments.command_game_environment import AppendCommandsAction
from agent_toolkit import Agent, logger

_LOGGER = logger.get_logger(__name__)
_LOGGER.setLevel('DEBUG')


_LOGGER = logger.get_logger(__name__)
_LOGGER.setLevel('DEBUG')


class ChatCommandAgent(Agent):
    """
    Given a command the agent will respond with a set of associated actions.

    Commands can be composed of different parts separated by :.

    Supported commands:
        repeat <N> <command>: Executes the command N times. Example: repeat 10 "move:right"
        echo:<message_to_echo>: Returns a chat event with message "Hi! You said <message_to_echo>".
        move:<[up|down|north|south|east|west]>: alters x, y, z coordinates in the desired direction.
            The value of the increase/decrease is determined by `self.position_movement_distance`.
            To move east/west increase or decrease the x coordinate respectively.
            To move south/north increase or decrease the z coordinate respectively.
            To move up/down increase or decrease the y coordinate respectively.
            See https://minecraft.fandom.com/wiki/Coordinates#World_coordinates
        look:<[up|down]> moves pitch by 45 degrees on the desired direction.
            Looking stratight up corresponds to pitch -90, and straigh down to pitch 90.
        exit: leave the game.

    If a command can't be parsed correctly, the agent returns NoAction()
    """

    def __init__(
        self,
        agent_service_id: str,
        agent_service_role_id: str,
        position_movement_distance=3,
    ) -> None:
        super().__init__(agent_service_id)
        self.agent_service_role_id = agent_service_role_id
        self.position_movement_distance = position_movement_distance

    def next_action(self, observation: Dict, *args) -> action_space.Action:
        next_command_index: int = observation["next_command_index"]

        latest_msg_idx: int = len(observation['conversation_history']) - 1
        if next_command_index > latest_msg_idx:
            return action_space.NoAction()

        # don't miss any unprocessed events
        self.current_message = observation['conversation_history'][
            next_command_index
        ]

        # Ignore agent's messages
        if self.current_message['role_id'] == self.agent_service_role_id:
            return action_space.NoAction()

        self.current_command = self.current_message['message'].lower()

        _LOGGER.debug("Processing command: " + self.current_command)

        # Documentation events. Useful from grouping different events visually in the log
        if self.current_command.startswith("-----"):
            _LOGGER.info(f"Starting new section: {self.current_command}")
            return action_space.NoAction()

        if self.current_command.startswith('exit'):
            return action_space.LeaveGameAction()

        if self.current_command.startswith('turn:change'):
            return action_space.NoAction()

        if self.current_command.startswith('echo:'):
            return self.handle_echo()

        # Process 'repeat' commands. They add more commands to the chat history.
        # A repeat command has the following shape
        #     repeat 10 "move:left"
        #       ^ will add 10 "move:left" commands to the chat history.
        p = re.compile(r".*?repeat (\d+) [“”\"'](.+)['\"“”]$")
        if match := p.match(self.current_command):
            return self.repeat(times=int(match.group(1)), command=match.group(2))

        # handle low level actions
        if self.agent_service_role_id not in observation['player_states']:
            return action_space.NoAction()

        current_agent_position = observation['player_states'][self.agent_service_role_id]['location']
        if self.current_command.startswith("move:"):
            return self.handle_move(current_agent_position)

        if self.current_command.startswith("rotate:"):
            return self.handle_rotate(current_agent_position)

        elif self.current_command.startswith("look:"):
            _LOGGER.info(f"Agent executing rotate command: {self.current_command}")
            return self.handle_look(current_agent_position)

        else:
            _LOGGER.warning(f"Got unknown command: {self.current_command}")
            return action_space.NoAction()

    def repeat(self, times, command):
        return AppendCommandsAction(
            commands=[command] * times,
            commander_role_id=self.current_message['role_id'])

    def handle_move(self, current_agent_position):

        move_action = action_space.MoveAction(
            x=current_agent_position['x'],
            z=current_agent_position['z'],
            y=current_agent_position['y'],
            pitch=current_agent_position['pitch'],
            yaw=current_agent_position['yaw']
        )

        # X - (+) east (-) west
        # Z - (-) north (+) south
        # https://minecraft.fandom.com/wiki/Coordinates#World_coordinates
        if "north" in self.current_command:
            # Decrease in Z is movement in northern direction
            move_action.z -= self.position_movement_distance

        elif "south" in self.current_command:
            # Increase in Z is movement in southern direction
            move_action.z += self.position_movement_distance

        elif "west" in self.current_command:
            move_action.x -= self.position_movement_distance

        elif "east" in self.current_command:
            move_action.x += self.position_movement_distance

        elif "up" in self.current_command:
            move_action.y += self.position_movement_distance

        elif "down" in self.current_command:
            move_action.y -= self.position_movement_distance

        else:
            _LOGGER.warning(f'Incorrect move command direction {self.current_command}')
            return action_space.NoAction()

        return move_action

    def handle_look(self, current_agent_position):
        # For the vertical rotation (pitch), -90.0 for straight up to 90.0 for straight down.
        # https://minecraft.fandom.com/wiki/Commands/tp/Before_Java_Edition_17w45a
        new_pitch = current_agent_position['pitch']
        if 'down' in self.current_command:
            new_pitch = max(current_agent_position['pitch'] + 45.0, 90.0)
        elif 'up' in self.current_command:
            new_pitch = max(current_agent_position['pitch'] - 45.0, -90.0)
        else:
            _LOGGER.warning(f'Incorrect look command direction {self.current_command}')
            return action_space.NoAction()
        return action_space.MoveAction(
            x=current_agent_position['x'],
            z=current_agent_position['z'],
            y=current_agent_position['y'],
            pitch=new_pitch,
            yaw=current_agent_position['yaw']
        )

    def handle_echo(self):
        _, words = self.current_command.split(':')[:2]
        return action_space.ChatAction(
            message=f"Hi! You said <{words}>"
        )

    def handle_rotate(self, current_agent_position):
        """Executes rotate command.

        Examples:
            rotate:right:90 Decreases yaw by 90 degrees
            rotate:left:3 Increases yaw by 3 degrees
        """
        command_components = self.current_command.split(':')
        if len(command_components) != 3:
            _LOGGER.warning(f'Incorrect rotate command {self.current_command}')
            return action_space.NoAction()
        _, direction, degrees = command_components
        degrees = int(degrees)
        # For horizontal rotation (yaw), -180.0 for due north, -90.0 for due east,
        # 0.0 for due south, 90.0 for due west, to 179.9 for just west of due north,
        # before wrapping back around to -180.0.
        # https://minecraft.fandom.com/wiki/Commands/tp/Before_Java_Edition_17w45a
        new_yaw = current_agent_position['yaw']
        if direction == 'right':
            new_yaw += degrees
        elif direction == 'left':
            new_yaw -= degrees
        else:
            _LOGGER.warning(f'Incorrect rotate command direction {self.current_command}')
            return action_space.NoAction()
        new_yaw = (new_yaw + 360) % 360
        return action_space.MoveAction(
            x=current_agent_position['x'],
            z=current_agent_position['z'],
            y=current_agent_position['y'],
            pitch=current_agent_position['pitch'],
            yaw=new_yaw
        )
