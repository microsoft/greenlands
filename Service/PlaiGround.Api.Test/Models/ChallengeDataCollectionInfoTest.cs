using Microsoft.AspNetCore.Routing.Constraints;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using NUnit.Framework;
using PlaiGround.Api.Events.v1;
using PlaiGround.Api.Models;
using PlaiGround.Api.Services;

namespace PlaiGround.Api.Test.Models;

internal class ChallengeDataCollectionInfoTest
{
    private IEventConversionService _eventConversionService;

    [SetUp]
    public void Setup()
    {
        _eventConversionService = new EventConversionService();
    }

    [Test]
    public async Task AgentChallengeDataCollectionInfoFromEvents_GivenEventsWithTwoCompletedTasks_ShouldReturnCorrectData()
    {
        // Arrange
        var task1Id = "0bf16099-8cdd-408e-afcf-20f0c92d918a";
        var task2Id = "44dbd0eb-5e38-4fe8-8c2b-7e9ea8c27224";
        var agentServiceId = "a0667ff4-929a-4a54-8ed6-e037d745e625";

        var game1Events = await LoadEventsFromFile("GameEvents1_Complete.json");
        var game2Events = await LoadEventsFromFile("GameEvents2_NotComplete.json");
        var game3Events = await LoadEventsFromFile("GameEvents3_Complete.json");

        var events = new List<BaseEvent>();
        events.AddRange(game1Events);
        events.AddRange(game2Events);
        events.AddRange(game3Events);

        var expectedAgentChallengeDataCollectionInfo = new AgentChallengeDataCollectionInfo
        {
            TaskIdToTaskDataCollectionInfo = new Dictionary<string, DataCollectionInfo>
            {
                [task1Id] = new DataCollectionInfo
                {
                    GamesPlayed = 2,
                    GamesWithTaskCompleted = 1
                },
                [task2Id] = new DataCollectionInfo
                {
                    GamesPlayed = 1,
                    GamesWithTaskCompleted = 1
                },
            },
            AgentIdToAgentDataCollectionInfo = new Dictionary<string, DataCollectionInfo>
            {
                [agentServiceId] = new DataCollectionInfo
                {
                    GamesPlayed = 2,
                    GamesWithTaskCompleted = 1
                }
            }
        };

        // Act
        var agentChallengeDataCollectionInfo = AgentChallengeDataCollectionInfo.FromEvents(new HashSet<string> { agentServiceId }, events);

        // Assert
        var expectedObject = JsonConvert.SerializeObject(expectedAgentChallengeDataCollectionInfo, Formatting.Indented);
        var actualObject = JsonConvert.SerializeObject(agentChallengeDataCollectionInfo, Formatting.Indented);
        Assert.AreEqual(expectedObject, actualObject);
    }

    private async Task<List<BaseEvent>> LoadEventsFromFile(string fileName)
    {
        var currentDir = Environment.CurrentDirectory;
        var fullPath = Path.GetFullPath(Path.Combine(currentDir, "TestData", "GameEvents", fileName));
        var fileText = await File.ReadAllTextAsync(fullPath);
        var unTypedEvent = JsonConvert.DeserializeObject<List<JObject>>(fileText);
        var typedEvents = unTypedEvent.Select(jObject => _eventConversionService.DeserializeEvent(jObject)).ToList();

        return typedEvents;
    }
}
