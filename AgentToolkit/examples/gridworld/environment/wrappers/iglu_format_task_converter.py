from typing import List, Tuple

import gym
import numpy

from examples.gridworld.environment.gridworld_game_environment import GridWorldGameEnvironment
from examples.gridworld.environment.task import Task, Tasks
from examples.gridworld.environment.utils import MC_MATRIAL_IDs


class IGLUFormatTaskConverterWrapper(gym.Wrapper):
    """
    Takes the data present in the local game state and converts it into an IGLU task.
    """

    env: GridWorldGameEnvironment

    def __init__(self, env: GridWorldGameEnvironment):
        super().__init__(env)

    @staticmethod
    def _minecraft_material_id_to_iglu_bid(material_id: int) -> int:
        mc_material_ids_to_gridworld_bid = {
            mc_material_id: gridworld_index
            for gridworld_index, mc_material_id in enumerate(MC_MATRIAL_IDs)
        }

        return mc_material_ids_to_gridworld_bid[material_id]

    def _get_block_loc_and_bid_from_plaiground_data(self, x, y, z, block) -> Tuple[int, int, int, int]:
        """
        Given the position where the Block instance is, return a new tuple
        (x,y,z,bid), where the plaiground location has been transformed into a
        Gridworld location and the Block's material to the corresponding
        Gridworld block ID.
        """

        # subtract -2 to Y since lowest non-floor block in plaiground it y==1, but in Gridworld
        # we want it to be at -1
        return (x, y-2, z, self._minecraft_material_id_to_iglu_bid(
            block.type
        ))

    def _get_target_grid(self) -> numpy.ndarray:
        sparse_tg = []
        for (x, y, z), block in self.env.target_states[0].blocks_in_world.items():
            # exclude blocks that are on the floor
            if y == 0:
                continue

            sparse_tg.append(
                self._get_block_loc_and_bid_from_plaiground_data(x, y, z, block)
            )

        return Tasks.to_dense(sparse_tg)

    def _get_starting_grid(self) -> List[Tuple[int, int, int, int]]:
        """
        Returns a list of tuples (x, y, z, block_id) for all blocks in the starting grid.
        """
        sparse_sg = []

        for (x, y, z), block in self.env.game_state.blocks_in_world.items():
            # exclude blocks that are on the floor
            if y == 0:
                continue

            sparse_sg.append(
                self._get_block_loc_and_bid_from_plaiground_data(x, y, z, block)
            )

        return sparse_sg

    def reset(self):
        self.env.reset()

        if self.env.target_states is None:
            raise ValueError("No target states were provided")

        if len(self.env.target_states) > 1:
            raise ValueError("Only one target state is supported")

        last_instruction = ""
        chat = ""

        if len(self.env.game_state.conversation_history) > 0:
            # last instruction only contains the last instruction in the chat history
            last_instruction = self.env.game_state.conversation_history[-1].message

            # chat includes all previous instructions as well as the last one
            chat = "\n".join(
                activity.message
                for activity in self.env.game_state.conversation_history
            )

        starting_sparse = self._get_starting_grid()
        target_dense = self._get_target_grid()

        task = Task(chat=chat,
                    target_grid=target_dense,
                    starting_grid=starting_sparse,
                    last_instruction=last_instruction)

        self.env.set_task(task)

        return self.env.to_observation_space()
