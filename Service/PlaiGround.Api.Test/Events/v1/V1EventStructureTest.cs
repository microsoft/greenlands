using Azure.Messaging.EventHubs;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using Newtonsoft.Json.Serialization;
using NUnit.Framework;
using PlaiGround.Api.Events;
using PlaiGround.Api.Events.v1;
using PlaiGround.Api.Events.v1.Helpers;
using PlaiGround.Api.Services;
using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;
using System.Reflection;

namespace PlaiGround.Api.Test.Evaluators;

public class V1EventStructureTest
{
    [SetUp]
    public void Setup()
    {
    }


    [Test]
    public void AllEventsShouldHaveEventTypeProperty()
    {
        var derivedEvents = new AddEventModels<BaseEvent>().getEventBaseTypeAndEventTypes();
        foreach (var eventType in derivedEvents)
        {
            // check that this event type has an "EventType" property and that it has the proper default value
            var eventTypeProp = eventType.GetProperty("EventType");
            Assert.NotNull(eventTypeProp, $"EventType property is not present in type {eventType.Name}");

            // check that the "EventType" property is properly set
            Assert.AreEqual((string)eventTypeProp.GetValue(Activator.CreateInstance(eventType)), eventType.Name,
               $"{eventTypeProp.Name} should have a default getter with the value of {eventType.Name}");


            var defaultValue = eventTypeProp.GetCustomAttribute<DefaultValueAttribute>();
            Assert.NotNull(defaultValue,
                $"{eventTypeProp.Name} property in {eventType.Name} does not have a DefaultValue attribute");

            Assert.AreEqual((string)(defaultValue.Value ?? ""), eventType.Name,
                $"{eventTypeProp.Name} property in {eventType.Name} does not have a DefaultValue attribute with value '{eventType.Name}'");
        }
    }

    [Test]
    public void DeserializedEventShouldBeCastableToBothBaseAndItself()
    {
        var blockPlaceEvent = new BlockPlaceEvent()
        {
            EventType = nameof(BlockPlaceEvent),
            GameId = "some game",
            RoleId = "some role id",
            TaskId = "some task id",
            TournamentId = "some tournament Id",
            GroupId = "a group id",
            Id = "e1af441a-440b-4bf5-9092-31c23881f30d",
            Source = EventSource.AgentService,
            Location = new Location()
            {
                X = 0.23f,
                Y = 43,
                Z = 54.0349f,
                Pitch = 349,
                Yaw = 12
            },
            Material = 42,
        };

        var serialized = JsonConvert.SerializeObject(blockPlaceEvent, new JsonSerializerSettings
        {
            ContractResolver = new CamelCasePropertyNamesContractResolver(),
            Formatting = Formatting.Indented,
        });

        // deserialize to JObject
        var jo = JObject.Parse(serialized);

        // cast to base event and work on that
        var baseEvent = jo.ToObject<BaseEvent>();
        Assert.AreEqual(nameof(BlockPlaceEvent), baseEvent.EventType);

        // we can then cast to the actual event
        var deserialized = jo.ToObject<BlockPlaceEvent>();
        Assert.AreEqual(42, deserialized.Material);
        Assert.AreEqual(EventSource.AgentService, deserialized.Source);

        var eventConversionService = new EventConversionService();
        var typedEvent = eventConversionService.DeserializeEvent(jo);
        var deserializedBlockPlaceEvent = (BlockPlaceEvent)typedEvent;
        Assert.AreEqual(nameof(BlockPlaceEvent), deserializedBlockPlaceEvent.EventType);
        Assert.AreEqual(42, deserializedBlockPlaceEvent.Material);
    }

    [Test]
    public void DeserializeEventData()
    {
        var blockPlaceEvent = new BlockPlaceEvent()
        {
            EventType = nameof(BlockPlaceEvent),
            GameId = "some game",
            RoleId = "some role id",
            TaskId = "some task id",
            TournamentId = "some tournament Id",
            GroupId = "a group id",
            Id = "e1af441a-440b-4bf5-9092-31c23881f30d",
            Source = EventSource.AgentService,
            Location = new Location()
            {
                X = 0.23f,
                Y = 43,
                Z = 54.0349f,
                Pitch = 349,
                Yaw = 12
            },
            Material = 42,
        };

        var serialized = JsonConvert.SerializeObject(blockPlaceEvent, new JsonSerializerSettings
        {
            ContractResolver = new CamelCasePropertyNamesContractResolver(),
            Formatting = Formatting.Indented,
        });

        // try deserializing EventData
        var eventData = new EventData(serialized);
        eventData.Properties.Add("eventType", blockPlaceEvent.EventType);

        var eventConversionService = new EventConversionService();

        var deserialized = eventConversionService.DeserializeEvent(eventData);
        Assert.AreEqual(nameof(BlockPlaceEvent), deserialized.EventType);

        var typedEvent = (BlockPlaceEvent)deserialized;

        Assert.AreEqual(42, typedEvent.Material);
        Assert.AreEqual("some tournament Id", typedEvent.TournamentId);
    }
}
