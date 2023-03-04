########################################################################
# NOTE:
# This file was taken as-is from
# https://github.com/iglu-contest/gridworld/blob/bac482340b1d4a9eb7a876dee99f83ccb88e4c56/gridworld/tasks/task.py
########################################################################

import numpy as np

BUILD_ZONE_SIZE_X = 11
BUILD_ZONE_SIZE_Z = 11
BUILD_ZONE_SIZE = 9, 11, 11


class Task:
    def __init__(
        self,
        chat,
        target_grid,
        last_instruction=None,
        starting_grid=None,
        full_grid=None,
        invariant=True
    ):
        """Creates a new Task represented with the past dialog and grid,
        the new instruction and target grid after completing the instruction.

        Parameters
        ----------
        chat : str
            Contatenation of all past utterances
        target_grid : numpy.array
            dense representation of the target
        last_instruction : string, optional
            the instruction corresponding to this step in the task,
            by default None
        starting_grid : sparse, optional
            sparse representation of the initial world, by default None
        full_grid : numpy.array, optional
            dense representation of the general target structure,
            by default None
        invariant : bool, optional
            by default True
        """
        self.chat = chat
        self.starting_grid = starting_grid
        self.last_instruction = last_instruction
        self.full_grid = full_grid
        self.admissible = [[] for _ in range(4)]
        self.target_size = (target_grid != 0).sum().item()
        self.full_size = self.target_size
        if full_grid is not None:
            self.full_size = (full_grid != 0).sum().item()
        self.target_grid = target_grid
        self.target_grids = [target_grid]
        full_grids = [full_grid]
        self.max_int = 0
        self.prev_grid_size = 0
        self.right_placement = 0
        self.wrong_placement = 0
        # fill self.target_grids with four rotations of the original grid around the vertical axis
        for _ in range(3):
            self.target_grids.append(
                np.zeros(target_grid.shape, dtype=np.int32))
            full_grids.append(np.zeros(target_grid.shape, dtype=np.int32))
            for x in range(BUILD_ZONE_SIZE_X):
                for z in range(BUILD_ZONE_SIZE_Z):
                    self.target_grids[-1][:, z, BUILD_ZONE_SIZE_X - x - 1] \
                        = self.target_grids[-2][:, x, z]
                    if full_grid is not None:
                        full_grids[-1][:, z, BUILD_ZONE_SIZE_X - x - 1] \
                            = full_grids[-2][:, x, z]
        # (dx, dz) is admissible iff the translation of target grid by (dx, dz) preserve (== doesn't cut)
        # target structure within original (unshifted) target grid
        if not invariant:
            self.admissible = [[(0, 0)]]
        else:
            for i in range(4):
                if full_grid is not None:
                    grid = full_grids[i]
                else:
                    grid = self.target_grids[i]
                for dx in range(-BUILD_ZONE_SIZE_X + 1, BUILD_ZONE_SIZE_X):
                    for dz in range(-BUILD_ZONE_SIZE_Z + 1, BUILD_ZONE_SIZE_Z):
                        sls_target = grid[:, max(dx, 0):BUILD_ZONE_SIZE_X + min(dx, 0),
                                          max(dz, 0):BUILD_ZONE_SIZE_Z + min(dz, 0):]
                        if (sls_target != 0).sum().item() == self.full_size:
                            self.admissible[i].append((dx, dz))

    def reset(self):
        """
        placeholder method to have uniform interface with `Tasks` class.
        Resets all fields at initialization of the new episode.
        """
        if self.starting_grid is not None:
            self.max_int = self.maximal_intersection(
                Tasks.to_dense(self.starting_grid))
        else:
            self.max_int = 0
        self.prev_grid_size = len(
            self.starting_grid) if self.starting_grid is not None else 0
        self.right_placement = 0
        self.wrong_placement = 0
        return self

    # placeholder methods for uniformity with Tasks class
    ###
    def __len__(self):
        return 1

    def __iter__(self):
        yield self

    ###

    def __repr__(self) -> str:
        instruction = self.last_instruction \
            if len(self.last_instruction) < 20 \
            else self.last_instruction[:20] + '...'
        return f"Task(instruction={instruction})"

    def step_intersection(self, grid):
        """
        Calculates the difference between the maximal intersection at previous step and the current one.
        Note that the method updates object fields to save the grid size.

        Args (grid): current grid
        """
        grid_size = (grid != 0).sum().item()
        wrong_placement = (self.prev_grid_size - grid_size)
        max_int = self.maximal_intersection(
            grid) if wrong_placement != 0 else self.max_int
        done = max_int == self.target_size

        self.prev_grid_size = grid_size
        right_placement = (max_int - self.max_int)

        self.max_int = max_int
        self.right_placement = right_placement
        self.wrong_placement = wrong_placement

        return right_placement, wrong_placement, done

    def argmax_intersection(self, grid):
        max_int, argmax = 0, (0, 0, 0)
        for i, admissible in enumerate(self.admissible):
            for dx, dz in admissible:
                x_sls = slice(max(dx, 0), BUILD_ZONE_SIZE_X + min(dx, 0))
                z_sls = slice(max(dz, 0), BUILD_ZONE_SIZE_Z + min(dz, 0))
                sls_target = self.target_grids[i][:, x_sls, z_sls]

                x_sls = slice(max(-dx, 0), BUILD_ZONE_SIZE_X + min(-dx, 0))
                z_sls = slice(max(-dz, 0), BUILD_ZONE_SIZE_Z + min(-dz, 0))
                sls_grid = grid[:, x_sls, z_sls]
                intersection = ((sls_target == sls_grid) &
                                (sls_target != 0)).sum().item()
                if intersection > max_int:
                    max_int = intersection
                    argmax = (dx, dz, i)
        return argmax

    def get_intersection(self, grid, dx, dz, rot):
        x_sls = slice(max(dx, 0), BUILD_ZONE_SIZE_X + min(dx, 0))
        z_sls = slice(max(dz, 0), BUILD_ZONE_SIZE_Z + min(dz, 0))
        sls_target = self.target_grids[rot][:, x_sls, z_sls]
        x_sls = slice(max(-dx, 0), BUILD_ZONE_SIZE_X + min(-dx, 0))
        z_sls = slice(max(-dz, 0), BUILD_ZONE_SIZE_Z + min(-dz, 0))
        sls_grid = grid[:, x_sls, z_sls]
        return ((sls_target == sls_grid) & (sls_target != 0)).sum().item()

    def maximal_intersection(self, grid):
        max_int = 0
        for i, admissible in enumerate(self.admissible):
            for dx, dz in admissible:
                x_sls = slice(max(dx, 0), BUILD_ZONE_SIZE_X + min(dx, 0))
                z_sls = slice(max(dz, 0), BUILD_ZONE_SIZE_Z + min(dz, 0))
                sls_target = self.target_grids[i][:, x_sls, z_sls]

                x_sls = slice(max(-dx, 0), BUILD_ZONE_SIZE_X + min(-dx, 0))
                z_sls = slice(max(-dz, 0), BUILD_ZONE_SIZE_Z + min(-dz, 0))
                sls_grid = grid[:, x_sls, z_sls]
                intersection = ((sls_target == sls_grid) &
                                (sls_target != 0)).sum().item()
                if intersection > max_int:
                    max_int = intersection
        return max_int


class Tasks:
    """
    Represents many tasks where one can be active
    """

    @classmethod
    def to_dense(cls, blocks, already_adjusted = False):
        if isinstance(blocks, (list, tuple)):
            if all(isinstance(b, (list, tuple)) for b in blocks):
                grid = np.zeros(BUILD_ZONE_SIZE, dtype=np.int)

                for x, y, z, block_id in blocks:
                    if already_adjusted:
                        newx = x
                        newz = z
                    else:
                        newx = x + BUILD_ZONE_SIZE_X // 2
                        newz = z + BUILD_ZONE_SIZE_Z // 2

                    grid[y + 1, newx, newz] = block_id

                blocks = grid

        return blocks

    @classmethod
    def to_sparse(cls, blocks):
        if isinstance(blocks, np.ndarray):
            idx = blocks.nonzero()
            types = [blocks[i] for i in zip(*idx)]
            blocks = [(*i, t) for *i, t in zip(*idx, types)]
            new_blocks = []
            for x, y, z, bid in blocks:
                new_blocks.append(
                    (x - BUILD_ZONE_SIZE_X // 2, y - 1, z - BUILD_ZONE_SIZE_Z // 2, bid))
            blocks = new_blocks
        return blocks

    def reset(self) -> Task:
        return NotImplemented

    def __len__(self) -> int:
        return NotImplemented

    def __iter__(self):
        return NotImplemented

    def set_task(self, task_id):
        return NotImplemented

    def get_target(self):
        return NotImplemented

    def set_task_obj(self, task: Task):
        return NotImplemented


class Subtasks(Tasks):
    """ Subtasks object represents a staged task where subtasks represent separate segments
    """

    def __init__(self, dialog, structure_seq, invariant=False, progressive=True) -> None:
        self.dialog = dialog
        self.invariant = invariant
        self.progressive = progressive
        self.structure_seq = structure_seq
        self.next = None
        self.full = False
        self.task_start = 0
        self.task_goal = 0
        self.full_structure = self.to_dense(self.structure_seq[-1])
        self.current = self.reset()

    def __getattr__(self, name):
        if name == 'current':
            return
        return getattr(self.current, name)

    def reset(self):
        """
        Randomly selects a random task within the task sequence.
        Each task is sampled with some non-trilial context (prior dialogs and
        starting structure) and one utterance goal instruction
        """
        if self.next is None:
            if len(self.structure_seq) == 1:
                turn = 0
            else:
                turn = np.random.choice(len(self.structure_seq))
            turn_goal = turn + 1
        else:
            turn = self.next
            turn_goal = self.next + 1
        self.task_start = turn
        self.task_goal = turn_goal
        self.current = self.create_task(self.task_start, self.task_goal)
        return self.current

    def __len__(self) -> int:
        return len(self.structure_seq)

    def __iter__(self):
        for i in range(len(self)):
            yield self.create_task(i - 1, i)

    def __repr__(self) -> str:
        return (f"Subtasks(total_steps={len(self.structure_seq)}, "
                f"current_task_start={self.task_start}, "
                f"current_task_end={self.task_goal})")

    def create_task(self, turn_start: int, turn_goal: int):
        """
        Returns a task with context defined by `turn_start` and goal defined
        by `turn_goal`

        """
        dialog = ''
        for turn in self.dialog[:turn_goal + 1]:
            if isinstance(turn, list):
                turn = '\n'.join(turn)
            dialog += '\n' + turn if len(dialog) > 0 else turn
        # dialog = '\n'.join([utt for utt in self.dialog[:turn_goal] if utt is not None])
        if turn_start == -1:
            initial_blocks = []
        else:
            initial_blocks = self.structure_seq[turn_start]
        tid = min(turn_goal, len(self.structure_seq) -
                  1) if not self.full else -1
        target_grid = self.structure_seq[tid]
        task = Task(
            dialog, target_grid=self.to_dense(target_grid),
            starting_grid=self.to_sparse(initial_blocks),
            full_grid=self.full_structure,
            last_instruction='\n'.join(self.dialog[tid])
        )
        # To properly init max_int and prev_grid_size fields
        task.reset()
        return task

    def step_intersection(self, grid):
        """

        """
        right_placement, wrong_placement, done = self.current.step_intersection(
            grid)
        if done and len(self.structure_seq) > self.task_goal and self.progressive:
            self.task_goal += 1
            self.current = self.create_task(self.task_start, self.task_goal)
            self.current.prev_grid_size = 0
            _, _, done = self.current.step_intersection(
                grid)  # to initialize things properly
        return right_placement, wrong_placement, done

    def set_task(self, task_id):
        self.task_id = task_id
        self.current = self.create_task(task_id)
        return self.current

    def set_task_obj(self, task: Task):
        self.task_id = None
        self.current = task
        return self.current
