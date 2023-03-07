import copy
import time
from typing import Dict, Optional

import gym
import requests
from greenlands_client.model.game_changes import GameChanges
from greenlands_client.model.game_state import GameState

from agent_toolkit import (CommonEventsProperties, GameEnvironment, LocalGameState,
                                      PlayerState, event_factory, logger, parse_location_string)

_LOGGER = logger.get_logger(__name__)


class RemoteTaskLoader(gym.Wrapper):
    env: GameEnvironment

    def __init__(
        self,
        env: GameEnvironment,
        task_id: str,
        taskdata_container_url: str
    ) -> None:
        super().__init__(env)

        self.task_id = task_id
        self.taskdata_container_url = taskdata_container_url

    def reset(self):
        # Download task data from blob
        base_url = f'{self.taskdata_container_url}/{self.task_id}'
        initial_game_state_url = f'{base_url}/initialGameState.json'
        _LOGGER.info(f"Download initial game state from blob:\n{initial_game_state_url}")
        initial_game_state = self._download_task_data_from_blob(initial_game_state_url)
        if initial_game_state is None:
            raise ValueError("Failed to download initial game state from blob")

        world_complete_blocks_url = f'{base_url}/initialWorldCompleteBlocks.json'
        _LOGGER.info(
            f"Download initial world complete blocks from blob:\n{world_complete_blocks_url}")
        world_complete_blocks = self._download_task_data_from_blob(world_complete_blocks_url)
        if world_complete_blocks is None:
            raise ValueError("Failed to download world complete blocks from blob")

        target_game_changes_url = f'{base_url}/targetGameChanges.json'
        _LOGGER.info(f"Download target game changes from blob:\n{target_game_changes_url}")
        target_game_changes = self._download_task_data_from_blob(target_game_changes_url)
        if target_game_changes is None:
            raise ValueError("Failed to download target game changes from blob")

        # Deserialize downloaded data
        deserialized_initial_game_state = event_factory.deserialize_greenlands_model(
            initial_game_state,
            GameState,
        )

        deserialized_world_complete_blocks = event_factory.deserialize_greenlands_model(
            world_complete_blocks,
            dict,
            # TODO: Should deserialize into the actual type of dict[LocationString, Block]
            # but deserializer doesn't recognize complex types correctly
        )

        _LOGGER.info(
            f"Set initial game state for task: {self.task_id}")
        self.env.game_state.set_initial_game_state(
            deserialized_initial_game_state,
            deserialized_world_complete_blocks,
        )

        deserialized_target_game_changes = []
        target_game_changes_list = event_factory.deserialize_greenlands_model(
            target_game_changes,
            list,
            # TODO: Should deserialize into actual type of list[GameChanges]
            # but deserializer doesn't recognize complex types correctly
        )

        # Because we only serialized as list above, we need to manually deserialize each GameChanges object in the list
        # to transform the properties. E.g. worldChanges into world_changes
        for target_game_change in target_game_changes_list:
            deserialized_target_game_change = event_factory.deserialize_greenlands_model(
                target_game_change,
                GameChanges,
            )
            deserialized_target_game_changes.append(deserialized_target_game_change)

        latest_game_state: LocalGameState = self.env.game_state

        for target_game_change_index, target_game_change in enumerate(
            deserialized_target_game_changes
        ):
            _LOGGER.debug(
                f"Applying target changes {target_game_change_index + 1} to saving target game state")

            latest_game_state_copy = copy.deepcopy(latest_game_state)

            latest_game_state = self.get_updated_game_state_from_applying_game_changes(
                latest_game_state_copy,
                target_game_change
            )

            self.env.target_states.append(latest_game_state)

        _LOGGER.info(
            f"Saved {len(self.env.target_states)} target game states for game "
            f"of task: {self.task_id}")

        self.episode_start_time_ms = time.time()

        return self.env.to_observation_space()

    # TODO: Move to utility add unit tests
    @staticmethod
    def get_updated_game_state_from_applying_game_changes(
        game_state_copy: LocalGameState,
        game_changes: GameChanges
    ) -> LocalGameState:
        """Returns the game state after applying the given game changes.
        """

        # set initial player states
        if game_changes.get('player_changes', None) is not None:
            for role_id, player_state in game_changes.get('player_changes').items():
                _LOGGER.debug(f"Applying changes to player state for role: {role_id}")

                game_state_copy.player_states[role_id] = PlayerState(
                    player_id="",  # unknown for now
                    role_id=role_id,
                    location=player_state["location"],
                )

        if game_changes.get('world_changes', None) is not None:
            if game_changes.get('world_changes').get('block_changes', None) is not None:
                block_changes = game_changes.get('world_changes').get('block_changes')

                _LOGGER.debug(
                    f"Applying {len(block_changes)} changes to blocks.")

                for location_string, block in block_changes.items():
                    location = parse_location_string(location_string)
                    game_state_copy.blocks_in_world[
                        (int(location.x), int(location.y), int(location.z))
                    ] = block

        return game_state_copy

    def _download_task_data_from_blob(self, blob_url: str) -> Optional[Dict]:
        response = requests.get(blob_url)
        if response.status_code == 200:
            return response.json()
        else:
            _LOGGER.error(f"Failed to download task data from blob: {blob_url}")
            return None
