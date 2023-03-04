########################################################################
# NOTE:
# This file was partially adapted from
# https://github.com/iglu-contest/gridworld/blob/bac482340b1d4a9eb7a876dee99f83ccb88e4c56/gridworld/utils.py
########################################################################

import math
import os
import shutil
from typing import Dict

import numba
import numpy as np

TICKS_PER_SEC = 60000

# Size of sectors used to ease block loading.
SECTOR_SIZE = 16

WALKING_SPEED = 5
FLYING_SPEED = 15

GRAVITY = 20.0
MAX_JUMP_HEIGHT = 1.2  # About the height of a block.
# To derive the formula for calculating jump speed, first solve
#    v_t = v_0 + a * t
# for the time at which you achieve maximum height, where a is the acceleration
# due to gravity and v_t = 0. This gives:
#    t = - v_0 / a
# Use t and the desired MAX_JUMP_HEIGHT to solve for v_0 (jump speed) in
#    s = s_0 + v_0 * t + (a * t^2) / 2
JUMP_SPEED = math.sqrt(2 * GRAVITY * MAX_JUMP_HEIGHT)
TERMINAL_VELOCITY = 50

PLAYER_HEIGHT = 2


def cube_vertices(x, y, z, n, top_only=False):
    """ Return the vertices of the cube at position x, y, z with size 2*n.

    """
    if top_only:
        return [
            x - n, y + n, z - n, x - n, y + n, z + n, x + n, y + n, z + n, x + n, y + n, z - n,
            # top
        ]
    return [
        x - n, y + n, z - n, x - n, y + n, z + n, x + n, y + n, z + n, x + n, y + n, z - n,  # top
        x - n, y - n, z - n, x + n, y - n, z - n, x + n, y - n, z + n, x - n, y - n, z + n,
        # bottom
        x - n, y - n, z - n, x - n, y - n, z + n, x - n, y + n, z + n, x - n, y + n, z - n,  # left
        x + n, y - n, z + n, x + n, y - n, z - n, x + n, y + n, z - n, x + n, y + n, z + n,  # right
        x - n, y - n, z + n, x + n, y - n, z + n, x + n, y + n, z + n, x - n, y + n, z + n,  # front
        x + n, y - n, z - n, x - n, y - n, z - n, x - n, y + n, z - n, x + n, y + n, z - n,  # back
    ]


def cube_normals(top_only=False):
    if top_only:
        return [
            0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0,  # top
        ]
    return [
        0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0,  # top
        0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0,  # bottom
        -1, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0,  # left
        1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0,  # right
        0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,  # front
        0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1,  # back
    ]


# TODO: Normalize is not really a normalization. It's more of a "floor"ing
@numba.jit
def normalize(position):
    """ Accepts `position` of arbitrary precision and returns the block
    containing that position.

    Parameters
    ----------
    position : tuple of len 3

    Returns
    -------
    block_position : tuple of ints of len 3

    """
    x, y, z = position
    x, y, z = (int(round(x)), int(round(y)), int(round(z)))
    return (x, y, z)


def tex_coord(x, y, n=4, split=False, side_n=0):
    """ Return the bounding vertices of the texture square.

    """
    m = 1.0 / n
    m1 = 1.0 / n / (2 if split else 1)
    if split:
        if side_n == 0:
            cx, cy = 0, 0
        elif side_n == 1:
            cx, cy = 0, 0.125
        elif side_n == 2:
            cx, cy = 0.125, 0
        elif side_n == 3:
            cx, cy = 0.125, 0.125
    else:
        cx, cy = 0, 0
    dx = x * m
    dy = y * m
    return (
        cx + dx, cy + dy,
        cx + dx + m1, cy + dy,
        cx + dx + m1, cy + dy + m1,
        cx + dx, cy + dy + m1
    )


def tex_coords(*side, top_only=False, split=False):
    """ Return a list of the texture squares for the top, bottom and side.

    """
    result = []
    if split:
        if top_only:
            return side
        else:
            # for _ in range(6):
            result += tex_coord(*side, split=split, side_n=1)
            result += tex_coord(*side, split=split, side_n=2)
            result += tex_coord(*side, split=split, side_n=0)
            result += tex_coord(*side, split=split, side_n=0)
            result += tex_coord(*side, split=split, side_n=3)
            result += tex_coord(*side, split=split, side_n=3)
    else:
        side = tex_coord(*side)
        for _ in range(1 if top_only else 6):
            result.extend(side)
    return result


WHITE = -1
GREY = 0
BLUE = 1
GREEN = 2
RED = 3
ORANGE = 4
PURPLE = 5
YELLOW = 6

MC_MATRIAL_IDs = [
    174,  # GRAY_WOOL
    178,  # BLUE_WOOL
    180,  # GREEN_WOOL
    181,  # RED_WOOL
    168,  # ORANGE_WOOL
    177,  # PURPLE_WOOL
    171,  # YELLOW_WOOL
]

# TODO we can piggyback on this to map BlockId to wool color in Plugin
id2texture = {
    WHITE: tex_coords(0, 0),
    GREY: tex_coords(1, 0),
    BLUE: tex_coords(2, 0, split=True),
    GREEN: tex_coords(3, 0, split=True),
    RED: tex_coords(0, 1, split=True),
    ORANGE: tex_coords(1, 1, split=True),
    PURPLE: tex_coords(2, 1, split=True),
    YELLOW: tex_coords(3, 1, split=True)
}

id2top_texture = {
    WHITE: tex_coords(0, 0, top_only=True),
    GREY: tex_coords(1, 0, top_only=True),
    BLUE: tex_coords(2, 0, top_only=True),
    GREEN: tex_coords(3, 0, top_only=True),
    RED: tex_coords(0, 1, top_only=True),
    ORANGE: tex_coords(1, 1, top_only=True),
    PURPLE: tex_coords(2, 1, top_only=True),
    YELLOW: tex_coords(3, 1, top_only=True)
}

FACES = [
    (0, 1, 0),
    (0, -1, 0),
    (-1, 0, 0),
    (1, 0, 0),
    (0, 0, 1),
    (0, 0, -1),
]

BUILD_ZONE_SIZE_X = 11
BUILD_ZONE_SIZE_Z = 11
BUILD_ZONE_SIZE = 9, 11, 11


def dump_dense_target_and_grid_representations(observation: Dict) -> None:
    """
    Gets an observation dict, that should contain the "target_grid" and "grid" fields,
    and outputs two sets of txt files, once for each of these fields. Each txt file in
    a set represents the item in the Y level of that set.

    Useful to verify that the target structure actually looks similar to the grid structure.
    """

    try:
        if os.path.exists("__dumps"):
            shutil.rmtree("__dumps")
    except Exception:
        pass

    for grid_name in ["target_grid", "grid"]:
        assert grid_name in observation, f"Expected observation to have a property called {grid_name}"

        # create folder to hold the set of files for the current "grid name"
        os.makedirs(f"__dumps/{grid_name}/")

        y_slices = observation[grid_name][:]

        for i, y_slice in enumerate(y_slices):
            # first use numpy to dump the current y slice
            path = f"__dumps/{grid_name}/layer-{i}.txt"
            np.savetxt(path, y_slice, fmt='%s')

            # ugly hack to make inspecting this files easier
            contents = open(path, "r").read()
            open(path, "w").write((contents
                                   .replace("0", "🌫️")
                                   .replace("1", "🟦")
                                   .replace("2", "🟩")
                                   .replace("3", "🟥")
                                   .replace("4", "🟧")
                                   .replace("5", "🟪")
                                   .replace("6", "🟨")
                                   .replace(" ", "")))
