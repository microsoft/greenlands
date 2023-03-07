"""Example actions an Agent can select.

In this example, we represent actions as instances, but they can be of any type.
This set of actions has a 1:1 equivalence with Greenlands events.
"""

from dataclasses import dataclass

ActionDataClassDecorator = dataclass(repr=True, eq=True)

@ActionDataClassDecorator
class Action:
    """Base class for actions on the Greenlands platform."""

    @property
    def name(self):
        """Returns the name of the class. It's equivalent to Action.__name__"""
        return self.__class__.__name__


@ActionDataClassDecorator
class NoAction(Action):
    pass


@ActionDataClassDecorator
class MoveAction(Action):
    x: int
    y: int
    z: int
    pitch: float = 0
    yaw: float = 0


@ActionDataClassDecorator
class ChatAction(Action):
    message: str


@ActionDataClassDecorator
class LeaveGameAction(Action):
    """The Agent decides to leave the Game."""
    pass


@ActionDataClassDecorator
class BlockPlaceAction(Action):
    x: int
    y: int
    z: int
    block_material_id: int


@ActionDataClassDecorator
class BlockRemoveAction(Action):
    x: int
    y: int
    z: int


@ActionDataClassDecorator
class TurnChangeAction(Action):
    """The Agent decides to end its turn."""
    pass
