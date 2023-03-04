########################################################################
# NOTE:
# This file was partially adapted from
# https://github.com/iglu-contest/gridworld/blob/bac482340b1d4a9eb7a876dee99f83ccb88e4c56/gridworld/env.py
########################################################################

import warnings
from copy import copy
from typing import Optional, Tuple

import gym
import numpy as np
from gym import Wrapper as gymWrapper
from gym.spaces import Box, Dict, Discrete, Space
from plaiground_client.model.location import Location
from plaiground_client.model.platform_player_joins_game_event import PlatformPlayerJoinsGameEvent
from plaiground_client.model.player_chat_event import PlayerChatEvent

from examples.gridworld.environment.task import Task, Tasks
from examples.gridworld.environment.utils import MC_MATRIAL_IDs
from examples.gridworld.environment.world import Agent, Vector2, Vector3, World
from plaiground_agent_toolkit import EventCallbackProvider, GameEnvironment, RegisteredEvent, logger
from plaiground_agent_toolkit.game_environment import TurnState

_LOGGER = logger.get_logger(f"plaiground_agent_toolkit.gridworld_command.{__name__}")


class String(Space):
    def __init__(self, ):
        super().__init__(shape=(), dtype=np.object_)

    def sample(self):
        return ''

    def contains(self, obj):
        return isinstance(obj, str)


class Wrapper(gymWrapper):
    def __getattr__(self, name):
        return getattr(self.env, name)


class GridWorldGameEnvironment(GameEnvironment):

    def __init__(
        self,
        role_id: str,
        callback_provider: EventCallbackProvider,
        game_state: World,
        render=False,
        render_size=(64, 64),
        max_steps=250,
        select_and_place=False,
        discretize=False,
        right_placement_scale=1.,
        wrong_placement_scale=0.1,
        target_in_obs=False,
        action_space='walking',
        vector_state=True,
        fake=False,
        name='',
        episode_timeout_seconds: int = 10
    ) -> None:
        super().__init__(
            role_id=role_id,
            callback_provider=callback_provider,
            initial_game_state=game_state,
            episode_timeout_seconds=episode_timeout_seconds
        )

        # Init agent and world
        self.agent = Agent(sustain=False)
        self.world = game_state

        # self.grid[Y, X, Z] = block id (int)
        self.grid = np.zeros((9, 11, 11), dtype=np.int32)
        self._task: Optional[Task] = None
        self._task_generator: Optional[Tasks] = None
        self.step_no = 0
        self.right_placement_scale = right_placement_scale
        self.wrong_placement_scale = wrong_placement_scale

        self.max_steps = max_steps
        self.world.add_callback('on_add', self._on_add_block)
        self.world.add_callback('on_remove', self._on_remove_block)
        self.world.add_callback('on_move', self._on_player_move)

        self.right_placement = 0
        self.wrong_placement = 0
        self.render_size = render_size
        self.select_and_place = select_and_place
        self.target_in_obs = target_in_obs
        self.vector_state = vector_state
        self.discretize = discretize
        self.action_space_type = action_space
        self.starting_grid = None
        self.fake = fake
        self._overwrite_starting_grid = None

        # initial position is [X, Y, Z]
        self.initial_position = (
            0, 0, 0)  # this is the center of the grid which defaults to [-5 to 5] in X and Z
        self.initial_rotation = (0, 0)  # the agent is facing North

        if action_space == 'walking':
            if discretize:
                self.action_space = Discrete(18)
            else:
                self.action_space = Dict({
                    'forward': Discrete(2),
                    'back': Discrete(2),
                    'left': Discrete(2),
                    'right': Discrete(2),
                    'jump': Discrete(2),
                    'attack': Discrete(2),
                    'use': Discrete(2),
                    'camera': Box(low=-5, high=5, shape=(2,)),
                    'hotbar': Discrete(7)
                })

        elif action_space == 'flying':
            self.action_space = Dict({
                'movement': Box(low=-1, high=1, shape=(3,), dtype=np.float32),
                'camera': Box(low=-5, high=5, shape=(2,), dtype=np.float32),
                'inventory': Discrete(7),
                'placement': Discrete(3),
            })
            self.agent.flying = True

        self.observation_space = {
            'inventory': Box(low=0, high=20, shape=(6,), dtype=np.float32),
            'compass': Box(low=-180, high=180, shape=(1,), dtype=np.float32),
            'dialog': String()
        }

        if vector_state:
            self.observation_space['agentPos'] = Box(
                low=np.array([-8, -2, -8, -90, 0], dtype=np.float32),
                high=np.array([8, 12, 8, 90, 360], dtype=np.float32),
                shape=(5,)
            )
            self.observation_space['grid'] = Box(
                low=-1, high=7, shape=(9, 11, 11), dtype=np.int32)

        if target_in_obs:
            self.observation_space['target_grid'] = Box(
                low=-1, high=7, shape=(9, 11, 11), dtype=np.int32
            )

        if render:
            self.observation_space['pov'] = Box(
                low=0, high=255, shape=(*self.render_size, 3),
                dtype=np.uint8
            )

        self.do_render = render

        self.observation_space = Dict(self.observation_space)
        self.max_int = 0
        self.name = name

        if render:
            from examples.gridworld.environment.render import Renderer, setup
            self.renderer = Renderer(self.world, self.agent,
                                     width=self.render_size[0], height=self.render_size[1],
                                     caption='Pyglet', resizable=False)
            setup()
        else:
            self.renderer = None
            self.world.initialize()

    @property
    def no_op_action(self) -> int:
        return 0

    @staticmethod
    def _transform_location_for_plaiground(x, y, z, yaw, pitch, is_block=False) -> Location:
        """
        Converts from env's grid coordinates to Minecraft's coordinates
        """

        x = float(x)
        y = float(y)
        z = float(z)
        pitch = float(pitch)
        yaw = float(yaw)

        x -= 5
        z -= 5

        # adjust Y position (correct for 0.25 because Gridworld sinks player into block)
        if is_block:
            y = y + 1
        else:
            y = y + 0.25

        # correct pitch since it is inverted
        pitch = -pitch

        # correct yaw since it is rotated by 180 degrees
        yaw = yaw + 180.

        # cut down possible excess
        yaw = yaw % 360.

        return Location(x, y, z, pitch, yaw)

    @staticmethod
    def _correct_position_to_env_grid(x, y, z) -> Vector3:
        """
        Converts from World.py coordinates to env's grid coordinates
        """
        # QUESTION: Why are we adjusting here? It doesn't seem to matter for Plaiground though

        x += 5
        z += 5
        y += 1

        return x, y, z

    def _on_player_move(self, position: Vector3, rotation: Vector2):
        a_x, a_y, a_z = self._correct_position_to_env_grid(*position)
        a_yaw, a_pitch = rotation

        self.callback_provider.player_move(
            new_location=self._transform_location_for_plaiground(
                a_x, a_y, a_z, a_yaw, a_pitch
            )
        )

    @staticmethod
    def _get_mc_material_id_from_block_kind(kind: int) -> int:
        """
        These mappings come directly from Plugin:

        ```
        for (var entry : BlockUtils.MATERIAL_IDS.entrySet()) {
          if (entry.getKey().name().contains("WOOL")) {
            MinecraftLogger.info(">> %s: %s".formatted(entry.getKey(), entry.getValue()));
          }
        }
        ```
        """
        # see the block ids that gridwrold uses in utils.py
        return MC_MATRIAL_IDs[kind]

    def _on_add_block(self, position: Vector3, kind: int, build_zone=True):
        """
        Callback that gets invoked when a block is added
        """

        # only track block additions that happened in the build zone
        if self.world.initialized and build_zone:
            # report the block add event to the callback provider
            b_x, b_y, b_z = self._correct_position_to_env_grid(*position)
            a_x, a_y, a_z = self._correct_position_to_env_grid(*self.agent.position)

            block_location = self._transform_location_for_plaiground(
                b_x, b_y, b_z, 0, 0,
                is_block=True
            )

            a_yaw, a_pitch = self.agent.rotation
            player_location = self._transform_location_for_plaiground(
                a_x, a_y, a_z, a_yaw, a_pitch
            )

            self.callback_provider.block_place(
                material=self._get_mc_material_id_from_block_kind(kind),
                block_location=block_location,
                player_location=player_location
            )

            # update env's grid state
            self.grid[b_y, b_x, b_z] = kind

    def _on_remove_block(self, position: Vector3, build_zone=True):
        """
        Callback that gets invoked when a block is broken
        """

        # only track block removals that happened in the build zone
        if self.world.initialized and build_zone:
            b_x, b_y, b_z = self._correct_position_to_env_grid(*position)

            if self.grid[b_y, b_x, b_z] == 0:
                world_non_empty_blocks = {x: y for x, y in self.world.world.items() if y != 0 and y != -1}
                raise ValueError(
                    f'Removal of non-existing block. address: x={b_x}, y={b_y}, z={b_z} (original pos: {position}); \n'
                    f'grid state: {self.grid.nonzero()[0]}; \n'
                    f'world state: {world_non_empty_blocks}; \n'
                    f'agent position: {self.agent.position}; \n'
                    f'agent rotation: {self.agent.rotation}; \n'
                )

            # report the block remove event to the callback provider
            a_x, a_y, a_z = self._correct_position_to_env_grid(*self.agent.position)

            block_location = self._transform_location_for_plaiground(
                b_x, b_y, b_z, 0, 0,
                is_block=True
            )

            a_yaw, a_pitch = self.agent.rotation
            player_location = self._transform_location_for_plaiground(
                a_x, a_y, a_z, a_yaw, a_pitch
            )

            self.callback_provider.block_remove(
                block_location=block_location,
                player_location=player_location
            )

            # update env's grid state
            self.grid[b_y, b_x, b_z] = 0

    def _get_position_and_rotation_from_location(self, location: Location) -> Tuple[Tuple[int, int, int], Tuple[int, int]]:
        # plaiground floor is 1 but gridworld floor is 0
        position = (int(location.x), int(location.y) - 1, int(location.z))

        # yaw is rotated 180 degrees and pitch is inverted
        yaw = (int(location.yaw) + 180) % 360
        pitch = -int(location.pitch)
        rotation = (yaw, pitch)

        return (position, rotation)

    def set_task(self, task: Task):
        """
        Assigns provided task into the environment. On each .reset, the env
        Queries the .reset method for the task object. This method should drop
        the task state to the initial one.
        Note that the env can only work with non-None task or task generator.
        """
        if self._task_generator is not None:
            warnings.warn("The .set_task method has no effect with an initialized tasks generator. "
                          "Drop it using .set_tasks_generator(None) after calling .set_task")

        self._task = task

        self.reset()

    def set_task_generator(self, task_generator: Tasks):
        """
        Sets task generator for the current environment. On each .reset, the environment
        queries the .reset method of generator which returns the next task according to the generator.
        Note that the env can only work with non-None task or task generator.
        """
        self._task_generator = task_generator
        self.reset()

    def deinitialize_world(self):
        self._overwrite_starting_grid = None
        self.initial_position = (0, 0, 0)
        self.initial_rotation = (0, 0)
        self.reset()

    @property
    def task(self):
        if self._task is None:
            if self._task_generator is None:
                raise ValueError('Task is not initialized! Initialize task before working with'
                                 ' the environment using .set_task method OR set tasks distribution using '
                                 '.set_task_generator method')
            self._task = self._task_generator.reset()
            self.starting_grid = self._task.starting_grid
        return self._task

    def to_observation_space(self) -> Dict:
        x, y, z = self.agent.position
        yaw, pitch = self.agent.rotation

        obs = {}
        obs['inventory'] = np.array(
            copy(self.agent.inventory), dtype=np.float32)
        obs['compass'] = np.array([yaw - 180., ], dtype=np.float32)
        obs['dialog'] = self._task.chat

        if self.vector_state:
            obs['grid'] = self.grid.copy().astype(np.int32)
            obs['agentPos'] = np.array([x, y, z, pitch, yaw], dtype=np.float32)

        if self.target_in_obs:
            obs['target_grid'] = self._task.target_grid.copy().astype(np.int32)

            # uncomment the line below if you want to create a dump of what the
            # target and grid states currently look like as grids of emojis! 1
            # grid for each Y level
            # dump_dense_target_and_grid_representations(obs)

        if self.do_render:
            obs['pov'] = self.render()[..., :-1]

        return obs

    def render(self,):
        if not self.do_render:
            raise ValueError('create env with render=True')
        return self.renderer.render()

    def reset(self):
        self.world.deinit()

        if self._task is None:
            if self._task_generator is None:
                raise ValueError('Task is not initialized! Initialize task before working with'
                                 ' the environment using .set_task method OR set tasks distribution using '
                                 '.set_task_generator method')
            else:
                # yield new task
                self._task = self._task_generator.reset()

        elif self._task_generator is not None:
            self._task = self._task_generator.reset()

        self.step_no = 0
        self._task.reset()

        # clear env's grid
        self.grid = np.zeros((9, 11, 11), dtype=np.int32)

        if self._overwrite_starting_grid is not None:
            self.starting_grid = self._overwrite_starting_grid
        else:
            self.starting_grid = self._task.starting_grid

        self._synthetic_init_grid = None
        if self.starting_grid is not None:
            self._synthetic_init_grid = Tasks.to_dense(self.starting_grid)
            self._synthetic_task = Task(
                # create a synthetic task with only diff blocks.
                # blocks to remove have negative ids.
                '', target_grid=self._task.target_grid - self._synthetic_init_grid
            )
            self._synthetic_task.reset()

        for block in set(self.world.placed):
            self.world.remove_block(block)

        if self.starting_grid is not None:
            # bid == means block id
            for x, y, z, bid in self.starting_grid:
                self.world.add_block((x, y, z), bid)

            # make grid reflect world's grid
            for position, bid in self.world.world.items():
                b_x, b_y, b_z = self._correct_position_to_env_grid(*position)
                self.grid[b_y, b_x, b_z] = bid

        if (
            (player_state := self.game_state.initial_player_states.get(self.role_id, None)) is not None
            and player_state.spawn_location is not None
        ):
            position, rotation = self._get_position_and_rotation_from_location(player_state.spawn_location)
            self.agent.position = position
            self.agent.rotation = rotation
        else:
            self.agent.position = self.initial_position
            self.agent.rotation = self.initial_rotation

        self.max_int = self._task.maximal_intersection(self.grid)

        self.prev_grid_size = len(self.grid.nonzero()[0])

        # 20 of each item. What the items are is unknown yet
        self.agent.inventory = [20 for _ in range(6)]

        if self.starting_grid is not None:
            # if the starting grid is not None then we populate the initial world above. We also simulate
            # the fact that the agent has placed these blocks by removing the respective colors from the
            # agent's inventory
            for _, _, _, color in self.starting_grid:
                self.agent.inventory[color - 1] -= 1

        self.world.initialize()

        obs = self.to_observation_space()

        # zero out agent pos
        obs['agentPos'] = np.array([0., 0., 0., 0., 0.], dtype=np.float32)

        return obs

    def apply_event(self, event: RegisteredEvent) -> None:
        if (
            isinstance(event, PlatformPlayerJoinsGameEvent)
            and self._was_event_caused_by_self(event)
        ):
            location = event.get('spawn_location')
            position, rotation = self._get_position_and_rotation_from_location(location)
            self.agent.position = position
            self.agent.rotation = rotation

        if (
            isinstance(event, PlatformPlayerJoinsGameEvent)
            or not self._was_event_caused_by_self(event)
        ):
            # add who caused the chat if it is a chat message
            if isinstance(event, PlayerChatEvent):
                if event.role_id and event.role_id == self.role_id:
                    event.message = f'<Builder> {event.message}'
                else:
                    event.message = f'<Architect> {event.message}'

            super().apply_event(event)

            # update Task's dialog if event is chat event
            if isinstance(event, PlayerChatEvent):
                self._task.chat = "\n".join(
                    activity.message
                    for activity in self.game_state.conversation_history
                )

                self._task.last_instruction = self.game_state.conversation_history[-1].message

    def close(self) -> None:
        if self.renderer is not None:
            self.renderer.close()
        return super().close()

    def step(self, action: int):
        # if we're starting a new turn then just return the observation space
        # and don't do anything else
        if self._turn_state == TurnState.TURN_ABOUT_TO_START:
            return self.to_observation_space(), 0, False, {}

        if self._task is None:
            if self._task_generator is None:
                raise ValueError('Task is not initialized! Initialize task before working with'
                                 ' the environment using .set_task method OR set tasks distribution using '
                                 '.set_task_generator method')
            else:
                raise ValueError(
                    'Task is not initialized! Run .reset() first.')

        self.step_no += 1

        # make the World simulate the provided action
        self.world.step(
            self.agent,
            action,
            select_and_place=self.select_and_place,
            action_space=self.action_space_type,
            discretize=self.discretize
        )

        synthetic_grid = self.grid - self._synthetic_init_grid
        right_placement, wrong_placement, done = self._synthetic_task.step_intersection(
            synthetic_grid
        )

        done = done or (self.step_no == self.max_steps)

        # QUESTION: do we need rewards for IGLU agents? No harm in leaving them though...
        if right_placement == 0:
            reward = wrong_placement * self.wrong_placement_scale
        else:
            reward = right_placement * self.right_placement_scale

        # Action==18 means "turn end"
        done = done or (action == 18)
        if done:
            self.step_no = 0

            agent_pos = self._correct_position_to_env_grid(*self.agent.position)
            agent_pos = self._transform_location_for_plaiground(*agent_pos, *self.agent.rotation)
            self.callback_provider.turn_change(agent_pos)

        return self.to_observation_space(), reward, done, {}


class SizeReward(Wrapper):
    def __init__(self, env):
        super().__init__(env)
        self.size = 0

    def reset(self):
        self.size = 0
        return super().reset()

    def step(self, action):
        obs, reward, done, info = super().step(action)
        intersection = self.unwrapped.max_int
        reward = max(intersection, self.size) - self.size
        self.size = max(intersection, self.size)
        reward += min(self.unwrapped.wrong_placement * 0.02, 0)
        return obs, reward, done, info


def create_env(
    role_id: str,
    task_id: str,
    taskdata_container_url: str,
    callback_provider: EventCallbackProvider,
    game_state: World,
    load_remote_task=True,
    render=False, render_size=(64, 64), discretize=True,
    size_reward=True, select_and_place=True,
    right_placement_scale=1, target_in_obs=False,
    vector_state=False, max_steps=250, action_space='walking',
    wrong_placement_scale=0.1, name='', fake=False
):
    env = GridWorldGameEnvironment(
        role_id=role_id,
        render=render,
        game_state=game_state,
        callback_provider=callback_provider,
        render_size=render_size,
        select_and_place=select_and_place,
        discretize=discretize,
        right_placement_scale=right_placement_scale,
        wrong_placement_scale=wrong_placement_scale,
        name=name,
        target_in_obs=target_in_obs,
        vector_state=vector_state,
        max_steps=max_steps,
        action_space=action_space,
        fake=fake
    )

    custom_grid = np.ones((9, 11, 11))
    env.set_task(Task("", custom_grid))

    if load_remote_task:
        from examples.gridworld.environment.wrappers.iglu_format_task_converter import \
            IGLUFormatTaskConverterWrapper
        from plaiground_agent_toolkit.wrappers.remote_task_loader import RemoteTaskLoader

        env = RemoteTaskLoader(
            env,
            task_id,
            taskdata_container_url)

        env = IGLUFormatTaskConverterWrapper(env)

    if size_reward:
        env = SizeReward(env)

    # env = Actions(env)
    return env


gym.envs.register(
    id='PlaigroundGridworld-v0',
    entry_point=create_env,
    kwargs={}
)

gym.envs.register(
    id='PlaigroundGridworldVector-v0',
    entry_point=create_env,
    kwargs={'vector_state': True, 'render': False}
)
