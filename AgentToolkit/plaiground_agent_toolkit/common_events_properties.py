from typing import Any, Dict


class CommonEventsProperties:
    """Holds the information necessary to construct events for a specific game.

    Changing this class will result in breaking changes.
    """

    def __init__(
        self,
        tournament_id: str,
        game_id: str,
        task_id: str,
        role_id: str,
        group_id: str,
    ) -> None:
        self.__tournament_id = tournament_id
        self.__game_id = game_id
        self.__task_id = task_id
        self.__role_id = role_id
        self.__group_id = group_id

    def to_dict(self) -> Dict[str, Any]:
        return {
            'tournament_id': self.__tournament_id,
            'game_id': self.__game_id,
            'task_id': self.__task_id,
            'role_id': self.__role_id,
            'group_id': self.__group_id,
        }

    @property
    def tournament_id(self):
        return self.__tournament_id

    @property
    def game_id(self):
        return self.__game_id

    @property
    def task_id(self):
        return self.__task_id

    @property
    def role_id(self):
        return self.__role_id

    @property
    def group_id(self):
        return self.__group_id
