########################################################################
# NOTE:
# This file was partially adapted from
# https://github.dev/iglu-contest/iglu-2022-rl-baseline/blob/bac482340b1d4a9eb7a876dee99f83ccb88e4c56/wrappers/common_wrappers.py
########################################################################


import logging
import os
from collections import OrderedDict
from typing import Generator

import gym
import numpy as np
from gym.spaces import Box

logger = logging.getLogger(__file__)
IGLU_ENABLE_LOG = os.environ.get('IGLU_ENABLE_LOG', '')


class Wrapper(gym.Wrapper):
    def stack_actions(self):
        if isinstance(self.env, Wrapper):
            return self.env.stack_actions()

    def wrap_observation(self, obs, reward, done, info):
        if hasattr(self.env, 'wrap_observation'):
            return self.env.wrap_observation(obs, reward, done, info)
        else:
            return obs


class ActionsWrapper(Wrapper):
    def wrap_action(self, action) -> Generator:
        raise NotImplementedError

    def stack_actions(self):
        def gen_actions(action):
            for action in self.wrap_action(action):
                wrapped = None
                if hasattr(self.env, 'stack_actions'):
                    wrapped = self.env.stack_actions()
                if wrapped is not None:
                    yield from wrapped(action)
                else:
                    yield action

        return gen_actions

    def step(self, action):
        total_reward = 0
        for a in self.wrap_action(action):
            obs, reward, done, info = super().step(a)
            total_reward += reward
            if done:
                return obs, total_reward, done, info
        return obs, total_reward, done, info


class ObsWrapper(Wrapper):
    def observation(self, obs, reward=None, done=None, info=None):
        raise NotImplementedError

    def wrap_observation(self, obs, reward, done, info):
        new_obs = self.observation(obs, reward, done, info)
        return self.env.wrap_observation(new_obs, reward, done, info)

    def reset(self):
        return self.observation(super().reset())

    def step(self, action):
        obs, reward, done, info = super().step(action)
        info['grid'] = obs['grid']
        info['agentPos'] = obs['agentPos']
       # info['obs'] = obs['pov']
        return self.observation(obs, reward, done, info), reward, done, info


def flat_action_space(action_space):
    if action_space == 'human-level':
        return flat_human_level
    else:
        raise Exception("Action space not found!")


def no_op():
    return OrderedDict([('attack', 0), ('back', 0), ('camera', np.array([0., 0.])),
                        ('forward', 0), ('hotbar', 0), ('jump', 0), ('left', 0), ('right', 0),
                        ('use', 0)])


def flat_human_level(env, camera_delta=5):
    binary = ['attack', 'forward', 'back', 'left', 'right', 'jump']
    discretes = [no_op()]
    for op in binary:
        dummy = no_op()
        dummy[op] = 1
        discretes.append(dummy)
    camera_x = no_op()
    camera_x['camera'][0] = camera_delta
    discretes.append(camera_x)
    camera_x = no_op()
    camera_x['camera'][0] = -camera_delta
    discretes.append(camera_x)
    camera_y = no_op()
    camera_y['camera'][1] = camera_delta
    discretes.append(camera_y)
    camera_y = no_op()
    camera_y['camera'][1] = -camera_delta
    discretes.append(camera_y)
    for i in range(6):
        dummy = no_op()
        dummy['hotbar'] = i + 1
        discretes.append(dummy)
    discretes.append(no_op())
    return discretes


class Discretization(ActionsWrapper):
    def __init__(self, env, flatten):
        super().__init__(env)
        camera_delta = 5
        self.discretes = flatten(env, camera_delta)
        self.action_space = gym.spaces.Discrete(len(self.discretes))
        self.old_action_space = env.action_space
        self.last_action = None

    def wrap_action(self, action=None, raw_action=None):
        if action is not None:
            action = self.discretes[action]
        elif raw_action is not None:
            action = raw_action
        yield action


class JumpAfterPlace(ActionsWrapper):
    def __init__(self, env):
        min_inventory_value = 5
        max_inventory_value = 12
        self.act_space = (min_inventory_value, max_inventory_value)
        super().__init__(env)

    def wrap_action(self, action=None):
        if (action > self.act_space[0]) and (action < self.act_space[1]) > 0:
            yield action
            yield 5
            yield 5
        else:
            yield action


class ColorWrapper(ActionsWrapper):
    def __init__(self, env):
        super().__init__(env)
        min_inventory_value = 5
        max_inventory_value = 12
        self.color_space = (min_inventory_value, max_inventory_value)

    def wrap_action(self, action=None):
        tcolor = np.sum(self.env.task.target_grid)
        if (action > self.color_space[0]) and (action < self.color_space[1]) and tcolor > 0:
            action = int(self.color_space[0] + tcolor)
        yield action


class VectorObservationWrapper(ObsWrapper):
    def __init__(self, env):
        super().__init__(env)

        if 'pov' in self.env.observation_space.keys():
            self.observation_space = gym.spaces.Dict({
                'agentPos': gym.spaces.Box(low=-5000.0, high=5000.0, shape=(5,)),
                'grid': gym.spaces.Box(low=0.0, high=6.0, shape=(9, 11, 11)),
                'inventory': gym.spaces.Box(low=0.0, high=20.0, shape=(6,)),
                'target_grid': gym.spaces.Box(low=0.0, high=6.0, shape=(9, 11, 11)),
                'obs': gym.spaces.Box(low=0, high=1, shape=(self.env.render_size[0], self.env.render_size[0], 3),
                                      dtype=np.float32)
            })
        else:
            self.observation_space = gym.spaces.Dict({
                'agentPos': gym.spaces.Box(low=-5000.0, high=5000.0, shape=(5,)),
                'grid': gym.spaces.Box(low=0.0, high=6.0, shape=(9, 11, 11)),
                'inventory': gym.spaces.Box(low=0.0, high=20.0, shape=(6,)),
                'target_grid': gym.spaces.Box(low=0.0, high=6.0, shape=(9, 11, 11))
            })

    def observation(self, obs, reward=None, done=None, info=None):
        if IGLU_ENABLE_LOG == '1':
            self.check_component(
                obs['agentPos'], 'agentPos', self.observation_space['agentPos'].low,
                self.observation_space['agentPos'].high
            )
            self.check_component(
                obs['inventory'], 'inventory', self.observation_space['inventory'].low,
                self.observation_space['inventory'].high
            )
            self.check_component(
                obs['grid'], 'grid', self.observation_space['grid'].low,
                self.observation_space['grid'].high
            )
        if info is not None:
            if 'target_grid' in info:
                target_grid = self.env.task.target_grid
            else:
                # logger.error(f'info: {info}')
                if hasattr(self.unwrapped, 'should_reset'):
                    self.unwrapped.should_reset(True)
                target_grid = self.env.task.target_grid
        else:
            target_grid = self.env.task.target_grid

        if 'pov' in self.env.observation_space.keys():
            return {
                'agentPos': obs['agentPos'],
                'grid': obs['grid'],
                'inventory': obs['inventory'],
                'target_grid': target_grid,
                'obs': obs['pov']
            }
        else:
            return {
                'agentPos': obs['agentPos'],
                'grid': obs['grid'],
                'inventory': obs['inventory'],
                'target_grid': target_grid,
            }

    def check_component(self, arr, name, low, hi):
        if (arr < low).any():
            logger.info(f'{name} is below level {low}:')
            logger.info((arr < low).nonzero())
            logger.info(arr[arr < low])
        if (arr > hi).any():
            logger.info(f'{name} is above level {hi}:')
            logger.info((arr > hi).nonzero())
            logger.info(arr[arr > hi])


class FakeObsWrapper(gym.ObservationWrapper):

    def __init__(self, env):
        super().__init__(env)

        self.observation_space = self.env.observation_space
        self.observation_space['obs'] = Box(0.0, 1.0, shape=(1,))

    def observation(self, observation):
        observation['obs'] = np.array([0.0])
        return observation


class RewardInObservationWrapper(Wrapper):
    """
    Includes the `reward` output of `step` as part of the observation dictionary.
    """

    def reset(self):
        observation = super().reset()

        # reward is 0.0 after a reset
        observation['reward'] = 0.0

        return observation

    def step(self, action):
        observation, reward, done, info = super().step(action)

        # add `reward` to the observation
        observation['reward'] = reward

        return observation, reward, done, info


class TurnEndObservationWrapper(Wrapper):
    """
    Includes the `done` output of `step` as part of the observation dictionary.
    """

    def reset(self):
        observation = super().reset()

        # done is False after a reset
        observation['done'] = False

        return observation

    def step(self, action):
        observation, reward, done, info = super().step(action)

        # add `done` to the observation so that agent knows when we've finished
        # a turn
        observation['done'] = done

        return observation, reward, done, info
