########################################################################
# NOTE:
# This file was partially adapted from
# https://github.com/iglu-contest/gridworld/blob/bac482340b1d4a9eb7a876dee99f83ccb88e4c56/gridworld/core/world.py
########################################################################

import math
from numbers import Number
from typing import Dict, Optional, Set, Tuple, Union

from examples.gridworld.environment.utils import (
        BLUE, FACES, FLYING_SPEED, GRAVITY, GREY, JUMP_SPEED,
        PLAYER_HEIGHT,
        TERMINAL_VELOCITY, WALKING_SPEED, WHITE, normalize)

from plaiground_agent_toolkit import LocalGameState, logger

_LOGGER = logger.get_logger(f"plaiground_agent_toolkit.gridworld_command.{__name__}")

Vector3 = Tuple[Number, Number, Number]
Vector2 = Tuple[Number, Number]


class Agent:
    PAD = 0.25

    __slots__ = (
        'flying',
        'strafe',
        'position',
        'rotation',
        'reticle',  # QUESTION: what is this?
        'sustain',
        'dy',
        'time_int_steps',
        'inventory',
        'active_block'
    )

    def __init__(self, sustain=False) -> None:
        # When flying gravity has no effect and speed is increased.
        self.flying = False

        # strafe represents movement in X and Z dimensions [-1, 1]
        self.strafe = [0, 0]

        self.rotation = (0, 0)  # yaw pitch
        self.position = (0, 0, 0)  # x y z position
        self.reticle = None  # ? unk

        # actions are long-lasting state switches. In other words, action is performed
        # every tick, until another action overwrites it
        self.sustain = sustain

        # Velocity in the y (upward) direction.
        self.dy = 0

        # how many sub-ticks are we to split the current tick in? Used to optimize collision detection
        self.time_int_steps = 2

        self.inventory = [
            20, 20, 20, 20, 20, 20
        ]

        # active material / inventory item
        self.active_block = BLUE


class World(LocalGameState):
    __slots__ = 'world', 'shown', 'placed', 'callbacks', 'initialized'

    def __init__(self) -> None:
        super(World, self).__init__()

        self.initialized = False

        # World holds a dict that associates locations tuple(xyz) with a material, which is the material
        # at that position in the world. The floor is initialized so that the blocks in the build
        # zone are WHITE, and all other blocks are GREY. These count as base blocks and cannot be removed.
        self.world: Dict[Vector3, int] = {}

        # `shown` is like `world` but does not contain the WHITE and GRAY blocks for the floor.
        self.shown: Dict = {}

        # Placed keeps a set that tells us which locations have already been occupied by a block. Not sure
        # why since we could get that information from `world`. The only way that `placed` is not exactly
        # the same as world would be if we add a block when the world in not initialized. Although I'm not
        # sure in which cases that would happen.
        self.placed: Set = set()

        self.callbacks: Dict = {
            'on_add': [],
            'on_remove': [],
            'on_move': [],
        }

    def add_callback(self, name, func) -> None:
        self.callbacks[name].append(func)

    # BLOCKS RELATED METHODS
    def deinit(self) -> None:
        for block in list(self.placed):
            self.remove_block(block)

        self.initialized = False
        for block in list(self.world.keys()):
            self.remove_block(block)

        self.world = {}
        self.shown = {}
        self.placed = set()

    def build_zone(self, x, y, z, pad=0) -> bool:
        # it seems that here we're hardcoding the build zone to go from
        # x =>  -5-pad  to  5+pad
        # z =>  -5-pad  to  5+pad
        # y =>  -1-pad  to  8+pad
        return (-5 - pad <= x <= 5 + pad
                and -5 - pad <= z <= 5 + pad
                and -1 - pad <= y < 8 + pad)

    def initialize(self) -> None:
        """ Initialize the world by placing all the initial blocks.

        GREY is used for things outside of the build zone, and WHITE is
        used for the floor of the build zone.
        """

        n = 18  # 1/2 width and height of world
        s = 1  # step size
        y = 0  # initial y height

        # the floor is a square that goes from (-18,-18) to (18,18) inclusive.
        # ! and it sits at y == -2
        for x in range(-n, n + 1, s):
            for z in range(-n, n + 1, s):
                # block color will be WHITE if the location is inside the build_zone,
                # otherwise it will be GRAY
                color = GREY if not self.build_zone(x, y, z) else WHITE

                # block is created at Y == -2 always
                self.add_block((x, y - 2, z), color)

        self.initialized = True

    def hit_test(self, position, vector, max_distance=8) -> Tuple[
            Optional[Vector3], Optional[Vector3]]:
        """
        Line of sight search from current position. If a block is
        intersected it is returned, along with the block previously in the line
        of sight.

        If no block is found, return None, None.

        Parameters
        ----------
        position : tuple of len 3
            The (x, y, z) position that defines the location the test will start from.
        vector : tuple of len 3
            The line of sight vector. It defines the direction in which we want
            to perform the hit test. All dimensions in this vector go from -1 to 1
        max_distance : int
            How many blocks away to search for a hit.

        """

        # make hit test more precise by using m as an inverse scaling factor for the step size
        m = 5

        x, y, z = position
        dx, dy, dz = vector
        previous = None

        for _ in range(max_distance * m):
            key = normalize((x, y, z))
            if key != previous and key in self.world:
                return key, previous

            previous = key

            x, y, z = x + dx / m, y + dy / m, z + dz / m

        return None, None

    def add_block(self, position: Vector3, texture: int) -> None:
        """ Add a block with the given `texture` and `position` to the world.

        Parameters
        ----------
        position : tuple of len 3
            The (x, y, z) position of the block to add.
        texture :
            The ID of the material we want to place.
        """
        if position in self.world:
            self.remove_block(position)

        self.world[position] = texture
        self.shown[position] = texture

        for cb in self.callbacks['on_add']:
            cb(position, texture, build_zone=self.build_zone(*position))

        if self.initialized:
            _LOGGER.debug(f"Block of material {texture} added to {position}")
            self.placed.add(position)

    def remove_block(self, position: Vector3) -> None:
        """ Remove the block at the given `position`.

        Parameters
        ----------
        position : tuple of len 3
            The (x, y, z) position of the block to remove.
        """
        del self.world[position]

        if position in self.shown:
            self.shown.pop(position)

            for cb in self.callbacks['on_remove']:
                cb(position, build_zone=self.build_zone(*position))

        if self.initialized:
            _LOGGER.debug(f"Block removed from {position}")

            # an example where this check is necessary would be when the agent
            # tries to remove a block that was already part of the initial world
            # (this was not placed explicitly by the agent so it won't be part
            # of the `placed` dict)
            if position in self.placed:
                self.placed.remove(position)

    # END BLOCKS RELATED METHODS

    # AGENT CONTROL METHODS
    def get_sight_vector(self, agent: Agent) -> Vector3:
        """
        Returns the current line of sight vector indicating the direction
        the player is looking.

        Returns a Vector3 where every dimension ranges from -1 to 1
        """
        # y ranges from -90 to 90, or -pi/2 to pi/2, so m ranges from 0 to 1 and
        # is 1 when looking ahead parallel to the ground and 0 when looking
        # straight up or down.
        yaw, pitch = agent.rotation

        m = math.cos(math.radians(pitch))

        # dy ranges from -1 to 1 and is -1 when looking straight down and 1 when
        # looking straight up.
        dy = math.sin(math.radians(pitch))
        dx = math.cos(math.radians(yaw - 90)) * m
        dz = math.sin(math.radians(yaw - 90)) * m

        return dx, dy, dz

    def get_motion_vector(self, agent: Agent) -> Vector3:
        """
        Returns the current motion vector indicating the velocity of the
        player.

        This velocity is calculated solely based on the agent's strafe property.
        It doesn't take gravity into consideration.

        Returns
        -------
        vector : tuple of len 3
            Tuple containing the velocity in x, y, and z respectively.

        """
        if any(agent.strafe):
            yaw, pitch = agent.rotation

            strafe = math.degrees(math.atan2(*agent.strafe))
            y_angle = math.radians(pitch)
            x_angle = math.radians(yaw + strafe)

            if agent.flying:
                m = math.cos(y_angle)
                dy = math.sin(y_angle)

                if agent.strafe[1]:
                    # Moving left or right.
                    dy = 0.0
                    m = 1

                if agent.strafe[0] > 0:
                    # Moving backwards.
                    dy *= -1

                # When you are flying up or down, you have less left and right
                # motion.
                dx = math.cos(x_angle) * m
                dz = math.sin(x_angle) * m

            else:
                dy = 0.0
                dx = math.cos(x_angle)  # 6.123233995736766e-17
                dz = math.sin(x_angle)  # 1.0

        else:
            dy = 0.0
            dx = 0.0
            dz = 0.0

        return dx, dy, dz

    def update(self, agent: Agent, dt=1.0 / 5) -> None:
        """ This method advances the simulation by dt time.

        Parameters
        ----------
        dt : float
            The change in time since the last call.

        """
        m = agent.time_int_steps
        dt = min(dt, 0.2)

        for _ in range(m):
            self._update(agent, dt / m)

        if not agent.sustain:
            agent.strafe = [0, 0]

            if agent.flying:
                agent.dy = 0

    def _update(self, agent: Agent, dt: float) -> None:
        """
        Private implementation of the `update()` method. This is where most
        of the motion logic lives, along with gravity and collision detection.

        This method advances the simulation by dt time.

        Parameters
        ----------
        dt : float
            The change in time since the last call.

        """
        # walking
        speed = FLYING_SPEED if agent.flying else WALKING_SPEED
        d = dt * speed  # distance covered this tick.
        dx, dy, dz = self.get_motion_vector(agent)

        # New position in space, before accounting for gravity.
        dx, dy, dz = dx * d, dy * d, dz * d

        # gravity
        if not agent.flying:
            # Update your vertical speed: if you are falling, speed up until you
            # hit terminal velocity; if you are jumping, slow down until you
            # start falling.
            agent.dy -= dt * GRAVITY

            # increase the amount of steps we want to split the next `update` in
            # based on the Y velocity of the agent (if the Y velocity is large then
            # we want to control updates more granularly so we're sure possible collisions
            # are caught).
            if agent.dy < -14:
                agent.time_int_steps = 12
            elif agent.dy < -10:
                agent.time_int_steps = 8
            elif agent.dy < -5:
                agent.time_int_steps = 4
            else:
                agent.time_int_steps = 2

            agent.dy = max(agent.dy, -TERMINAL_VELOCITY)

        # update loop's dy with agent's dy
        dy += agent.dy * dt

        # collisions
        # Directions work the same as in Minecraft: South is positive Z, and East is positive X
        x, y, z = agent.position
        candidate_position = (x + dx, y + dy, z + dz)

        # don't allow the agent to move outside of the build zone
        if self.build_zone(*candidate_position, pad=2):
            x, y, z = self.collide(agent, candidate_position, PLAYER_HEIGHT)

        elif not agent.flying:
            x, y, z = self.collide(agent, (x, y + dy, z), PLAYER_HEIGHT)

        if (
            round(agent.position[0], 2) != round(x, 2)
            or round(agent.position[1], 2) != round(y, 2)
            or round(agent.position[2], 2) != round(z, 2)
        ):
            yaw, pitch = agent.rotation
            _LOGGER.debug(f"Agent moved to "
                          f"x:{round(x, 2)} "
                          f"y:{round(y, 2)} "
                          f"z:{round(z, 2)} "
                          f"pitch:{round(pitch, 2)} "
                          f"yaw:{round(yaw, 2)} "
                          f"selected_block:{agent.active_block}")

        # finally update agent's position with calculated coordinates
        agent.position = (x, y, z)

        if self.initialized:
            for cb in self.callbacks['on_move']:
                cb(agent.position, agent.rotation)

    def collide(self, agent: Agent, position: Vector3, height: Number, new_blocks=None) -> Vector3:
        """
        Checks to see if the player at the given `position` and `height`
        is colliding with any blocks in the world.

        Parameters
        ----------
        position : tuple of len 3
            The (x, y, z) position to check for collisions at.
        height : int or float
            The height of the player.

        Returns
        -------
        position : tuple of len 3
            The new position of the player taking into account collisions.

        """
        # How much overlap with a dimension of a surrounding block you need to
        # have to count as a collision. If 0, touching terrain at all counts as
        # a collision. If .49, you sink into the ground, as if walking through
        # tall grass. If >= .5, you'll fall through the ground.
        pad = Agent.PAD
        p = list(position)
        np = normalize(position)

        for face in FACES:  # check all surrounding blocks
            for i in range(3):  # check each dimension independently
                if not face[i]:
                    continue

                # How much overlap you have with this dimension.
                d = (p[i] - np[i]) * face[i]
                if d < pad:
                    continue

                for dy in range(int(height)):  # check each height
                    op = list(np)
                    op[1] -= dy
                    op[i] += face[i]

                    if tuple(op) not in self.world \
                            and (new_blocks is None or tuple(op) not in new_blocks):
                        continue

                    p[i] -= (d - pad) * face[i]
                    if face == (0, -1, 0) or face == (0, 1, 0):
                        # You are colliding with the ground or ceiling, so stop
                        # falling / rising.
                        agent.dy = 0

                    break

        return tuple(p)

    def place_or_remove_block(self, agent: Agent, remove: bool, place: bool) -> None:
        if place and remove:
            _LOGGER.warning("Tried to place and remove a block at the same time.")
            return

        if not place and not remove:
            # ignore action if there isn't anything we want to do
            return

        vector = self.get_sight_vector(agent)
        block, previous = self.hit_test(agent.position, vector)

        if place:
            # only place a block if the agent is actually looking at a solid block. If it is then
            # `previous` will contain the location of the empty block immediately before the "solid"
            # block hit by the hit_test
            if previous:
                # if the agent has enough of the selected block/material AND the target location is
                # inside the build zone..
                if agent.inventory[agent.active_block - 1] > 0 and self.build_zone(*previous):
                    x, y, z = agent.position
                    y = y - (PLAYER_HEIGHT - 1) + Agent.PAD

                    bx, by, bz = previous
                    bx -= 0.5
                    bz -= 0.5

                    if not (bx <= x <= bx + 1
                            and bz <= z <= bz + 1
                            and (by <= y <= by + 1
                                 or by <= (y + 1) <= by + 1)):
                        self.add_block(previous, agent.active_block)
                        agent.inventory[agent.active_block - 1] -= 1

        if remove and block:
            texture = self.world[block]

            # don't allow removing blocks that are part of the initial world (the floor of the build zone)
            if texture != GREY and texture != WHITE:
                self.remove_block(block)
                agent.inventory[texture - 1] += 1

    def get_focused_block(self, agent: Agent) -> Optional[Vector3]:
        """
        Returns the block location the agent is current looking at. If the agent is not looking at
        anything then None will be returned.
        """
        vector = self.get_sight_vector(agent)
        return self.hit_test(agent.position, vector)[0]

    def move_camera(self, agent: Agent, dx: float, dy: float):
        yaw, pitch = agent.rotation
        yaw, pitch = yaw + dx, pitch + dy
        pitch = max(-90, min(90, pitch))

        agent.rotation = (yaw, pitch)

        if self.initialized:
            for cb in self.callbacks['on_move']:
                cb(agent.position, agent.rotation)

    def movement(
        self,
        agent: Agent,
        strafe: Vector2,
        dy: float,
        inventory: Optional[int] = None,
    ) -> None:

        agent.strafe[0] += strafe[0]
        agent.strafe[1] += strafe[1]

        if dy != 0 and agent.dy == 0:
            agent.dy = JUMP_SPEED * dy

        if agent.flying and dy == 0:
            agent.dy = 0

        if inventory is not None:
            if inventory < 1 or inventory > 6:
                raise ValueError(f'Bad inventory id: {inventory}')

            agent.active_block = inventory

    # END AGENT CONTROL

    # UNIFIED AGENT CONTROL
    def parse_walking_discrete_action(self, action: int) -> Tuple[
            Vector2, int, int, Vector2, bool, bool]:
        # 0 noop; 1 forward; 2 back; 3 left; 4 right; 5 jump; 6-11 hotbar; 12 camera left;
        # 13 camera right; 14 camera up; 15 camera down; 16 attack; 17 use;
        # action = list(action).index(1)
        strafe = [0, 0]
        camera = [0, 0]
        dy = 0
        inventory = None
        remove = False
        add = False

        if action == 1:
            strafe[0] += -1
        elif action == 2:
            strafe[0] += 1
        elif action == 3:
            strafe[1] += -1
        elif action == 4:
            strafe[1] += 1
        elif action == 5:
            dy = 1
        elif 6 <= action <= 11:
            inventory = action - 5
        elif action == 12:
            camera[0] = -5
        elif action == 13:
            camera[0] = 5
        elif action == 14:
            camera[1] = -5
        elif action == 15:
            camera[1] = 5
        elif action == 16:
            remove = True
        elif action == 17:
            add = True
        return strafe, dy, inventory, camera, remove, add

    def parse_walking_action(self, action) -> Tuple[Vector2, int, int, Vector2, bool, bool]:
        strafe = [0, 0]
        if action['forward']:
            strafe[0] += -1
        if action['back']:
            strafe[0] += 1
        if action['left']:
            strafe[1] += -1
        if action['right']:
            strafe[1] += 1

        jump = int(action['jump'])

        if action['hotbar'] == 0:
            inventory = None
        else:
            inventory = action['hotbar']

        camera = action['camera']
        remove = bool(action['attack'])
        add = bool(action['use'])

        return strafe, jump, inventory, camera, remove, add

    def parse_flying_action(self, action) -> Tuple[Vector2, int, int, Vector2, bool, bool]:
        """
        Args:
            action: dictionary with keys:
              * 'movement':  Box(low=-1, high=1, shape=(3,)) - forward/backward, left/right,
                  up/down movement
              * 'camera': Box(low=[-180, -90], high=[180, 90], shape=(2,)) - camera movement (yaw, pitch)
              * 'inventory': Discrete(7) - 0 for no-op, 1-6 for selecting block color
              * 'placement': Discrete(3) - 0 for no-op, 1 for placement, 2 for breaking
        """
        strafe = list(action['movement'][:2])
        dy = action['movement'][2]
        camera = list(action['camera'])
        inventory = action['inventory'] if action['inventory'] != 0 else None
        add = action['placement'] == 1
        remove = action['placement'] == 2

        return strafe, dy, inventory, camera, remove, add

    def step(
        self,
        agent: Agent,
        action: Union[Dict, int],
        # select_and_place means that when the agent selects a new block (with the hotbar-X
        # actions), it will place it immediately.
        select_and_place=False,
        action_space='walking',
        discretize=True
    ) -> None:
        if action_space == 'walking':
            if discretize:
                tup = self.parse_walking_discrete_action(action)
            else:
                tup = self.parse_walking_action(action)

        elif action_space == 'flying':
            tup = self.parse_flying_action(action)

        strafe, dy, inventory, camera, remove, add = tup

        if select_and_place and inventory is not None:
            add = True
            remove = False

        self.movement(agent, strafe=strafe, dy=dy, inventory=inventory)
        self.move_camera(agent, *camera)
        self.place_or_remove_block(agent, remove=remove, place=add)
        self.update(agent, dt=1 / 20.)

        yaw, pitch = agent.rotation
        while yaw > 360:
            yaw -= 360
        while yaw < 0:
            yaw += 360

        agent.rotation = (yaw, pitch)
    # END UNIFIED AGENT CONTROL
