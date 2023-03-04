import unittest

from plaiground_client.model import (block_place_event, event_source, location,
                                     platform_game_start_event)

from agent_toolkit.event_factory import PlaigroundEventFactory


class PlaigroundEventFactoryTests(unittest.TestCase):
    """Tests for PlaigroundEventFactory serialization/deserialization functions.

    There are three main types of events in OpenApi:
        * ModelSimple: Parent class of models whose type != object in their
            swagger/openapi, i.e, basic types like strings, ints, etc.
        * ModelNormal: Parent class of models whose type == object in their
            swagger/openapi
        * ModelComposed: Parent class of events whose type == object in their
            swagger/openapi and have oneOf/allOf/anyOf

    Models can also be attributes of other model. The instantiation of each
    model follows a slightly different path. We test for serialization each
    type of model. Currently, there are no ModelComposed events in
    the platform.
    """

    def test_to_dict_simple_event(self):
        """Test the deserialization of a model with a ModelSimple attribute."""
        source = 'MinecraftPlugin'
        event = platform_game_start_event.PlatformGameStartEvent(
            id='new_event',
            game_id='new_game',
            produced_at_datetime='2022-08-18T19:10:03.487Z',
            task_id='old_task',
            tournament_id='old_tournament',
            source=event_source.EventSource(source),  # ModelSimple
            group_id=None
        )

        expected_dict = {
            'id': 'new_event',
            'gameId': 'new_game',
            'producedAtDatetime': '2022-08-18T19:10:03.487Z',
            'taskId': 'old_task',
            'tournamentId': 'old_tournament',
            'source': source,
            'eventType': 'PlatformGameStartEvent',
            'groupId': None
        }

        self.assertDictEqual(
            expected_dict, PlaigroundEventFactory.to_dict(event))

    def test_to_dict_normal_event(self):
        """Test the deserialization of a model with a ModelNormal attribute."""
        source = 'MinecraftPlugin'
        new_location = location.Location(0.0, 1.0, 2.0, 3.0, 4.0)
        event = block_place_event.BlockPlaceEvent(
            id='new_event',
            game_id='new_game',
            produced_at_datetime='2022-08-18T19:10:03.487Z',
            task_id='old_task',
            tournament_id='old_tournament',
            source=event_source.EventSource(source),
            role_id='some_role',
            location=new_location,  # ModelNormal
            material=1,
            group_id=None
        )

        expected_dict = {
            'id': 'new_event',
            'gameId': 'new_game',
            'producedAtDatetime': '2022-08-18T19:10:03.487Z',
            'taskId': 'old_task',
            'tournamentId': 'old_tournament',
            'source': source,
            'eventType': 'BlockPlaceEvent',
            'roleId': 'some_role',
            'location': {'pitch': 3.0, 'x': 0.0, 'y': 1.0, 'yaw': 4.0,
                         'z': 2.0},
            'material': 1,
            'groupId': None
        }

        self.assertDictEqual(
            expected_dict, PlaigroundEventFactory.to_dict(event))

    def test_from_dict_simple_event(self):
        """Test the deserialization of a model with a ModelSimple attribute."""
        source = 'MinecraftPlugin'
        event_dict = {
            'id': 'new_event',
            'game_id': 'new_game',
            'produced_at_datetime': '2022-08-18T19:10:03.487Z',
            'task_id': 'old_task',
            'tournament_id': 'old_tournament',
            'source': source,
            'eventType': 'PlatformGameStartEvent',
            'group_id': None
        }

        event = PlaigroundEventFactory.from_dict(event_dict)
        self.assertEqual(platform_game_start_event.PlatformGameStartEvent,
                         event.__class__)
        self.assertEqual(event_dict['id'], event.id)
        self.assertEqual(event_dict['game_id'], event.game_id)
        self.assertEqual(event_dict['produced_at_datetime'], event.produced_at_datetime)
        self.assertEqual(event_dict['task_id'], event.task_id)
        self.assertEqual(event_dict['tournament_id'], event.tournament_id)
        self.assertEqual(event_source.EventSource(source), event.source)
        self.assertEqual(event_dict['group_id'], event.group_id)

    def test_from_dict_normal_event(self):
        """Test the deserialization of a model with a ModelNormal attribute."""
        source = 'MinecraftPlugin'
        event_dict = {
            'id': 'new_event',
            'game_id': 'new_game',
            'produced_at_datetime': '2022-08-18T19:10:03.487Z',
            'task_id': 'old_task',
            'tournament_id': 'old_tournament',
            'source': source,
            'eventType': 'BlockPlaceEvent',
            'role_id': 'some_role',
            'location': {'pitch': 3.0, 'x': 0.0, 'y': 1.0, 'yaw': 4.0,
                         'z': 2.0},
            'material': 1,
            'group_id': None
        }
        new_location = location.Location(0.0, 1.0, 2.0, 3.0, 4.0)

        event = PlaigroundEventFactory.from_dict(event_dict)
        self.assertEqual(block_place_event.BlockPlaceEvent, event.__class__)
        self.assertEqual(event_dict['id'], event.id)
        self.assertEqual(event_dict['game_id'], event.game_id)
        self.assertEqual(event_dict['produced_at_datetime'], event.produced_at_datetime)
        self.assertEqual(event_dict['task_id'], event.task_id)
        self.assertEqual(event_dict['tournament_id'], event.tournament_id)
        self.assertEqual(event_source.EventSource(source), event.source)
        self.assertEqual(event_dict['role_id'], event.role_id)
        self.assertEqual(new_location, event.location)
        self.assertEqual(event_dict['material'], event.material)
        self.assertEqual(event_dict['group_id'], event.group_id)


if __name__ == '__main__':
    unittest.main()
