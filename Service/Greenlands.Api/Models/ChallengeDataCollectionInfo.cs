using Newtonsoft.Json;

namespace Greenlands.Api.Models;

public class DataCollectionInfo
{
    public int? GamesPlayed { get; init; }

    public int? GamesWithTaskCompleted { get; init; }
}

public class ChallengeDataCollectionInfo
{
    [Required]
    public Dictionary<string, DataCollectionInfo> TaskIdToTaskDataCollectionInfo { get; init; } = new Dictionary<string, DataCollectionInfo>();

    public static ChallengeDataCollectionInfo FromEvents(IList<BaseEvent> events)
    {
        var agentChallengeDataCollectionInfo = AgentChallengeDataCollectionInfo.FromEvents(new HashSet<string>(), events);
        return agentChallengeDataCollectionInfo;
    }
}

public class AgentChallengeDataCollectionInfo : ChallengeDataCollectionInfo
{
    [Required]
    public Dictionary<string, DataCollectionInfo> AgentIdToAgentDataCollectionInfo { get; init; } = new Dictionary<string, DataCollectionInfo>();

    public static AgentChallengeDataCollectionInfo FromEvents(ISet<string> agentServiceIds, IList<BaseEvent> events)
    {
        var agentChallengeDataCollectionInfo = new AgentChallengeDataCollectionInfo();

        var challengeData = ChallengeData.FromEvents(events);
        var taskDatas = challengeData.Tournaments.SelectMany(tournament => tournament.Tasks);
        foreach (var taskData in taskDatas)
        {
            var gamesWithTaskCompletedSuccessfully = taskData.Games.Where(g => g.Events.Any(e =>
            {
                switch (e.EventType)
                {
                    case nameof(PlatformTaskCompletedEvent):
                        {
                            var taskCompletedEvent = (PlatformTaskCompletedEvent)e;
                            var isSuccess = taskCompletedEvent.CompletionType == GameCompletionType.PLAYER_COMMAND_SUCCESS;

                            return isSuccess;
                        }
                }

                return false;
            }));

            var taskDataCollectionInfo = new DataCollectionInfo
            {
                GamesPlayed = taskData.Games.Count,
                GamesWithTaskCompleted = gamesWithTaskCompletedSuccessfully.Count()
            };

            agentChallengeDataCollectionInfo.TaskIdToTaskDataCollectionInfo.Add(taskData.Id, taskDataCollectionInfo);
        }

        // Create a mapping between the Agents and the Games they have played so we can compute metrics
        var agentIdToGameDatas = new Dictionary<string, IList<GameData>>();

        var gameDatas = challengeData.Tournaments.SelectMany(tournament => tournament.Tasks).SelectMany(taskData => taskData.Games);
        foreach (var gameData in gameDatas)
        {
            // Manually group games if an Agent joined the game
            foreach (var e in gameData.Events)
            {
                var isPlatformPlayerJoinEvent = e.EventType == nameof(PlatformPlayerJoinsGameEvent);
                if (isPlatformPlayerJoinEvent)
                {
                    var platformPlayerJoinEvent = e as PlatformPlayerJoinsGameEvent;
                    var isPlayerAnAgent = agentServiceIds.Contains(platformPlayerJoinEvent.PlayerId);
                    if (isPlayerAnAgent)
                    {
                        var agentId = platformPlayerJoinEvent.PlayerId;
                        // If agent does not have list of games already in dictionary, create it
                        var agentGames = agentIdToGameDatas.GetValueOrDefault(agentId);
                        if (agentGames == null)
                        {
                            agentGames = new List<GameData> { };
                            agentIdToGameDatas.Add(agentId, agentGames);
                        }

                        agentGames.Add(gameData);
                    }
                }
            }
        }

        foreach (var (agentId, agentGameDatas) in agentIdToGameDatas)
        {
            var numberOfGamesAgentCompletedTask = 0;
            foreach (var agentGameData in agentGameDatas)
            {
                var didAgentCompleteTask = agentGameData.Events.Any(e => e.EventType == nameof(PlatformTaskCompletedEvent));
                if (didAgentCompleteTask)
                {
                    numberOfGamesAgentCompletedTask += 1;
                }
            }

            var agentDataCollectionInfo = new DataCollectionInfo
            {
                GamesPlayed = agentGameDatas.Count,
                GamesWithTaskCompleted = numberOfGamesAgentCompletedTask
            };

            agentChallengeDataCollectionInfo.AgentIdToAgentDataCollectionInfo.Add(agentId, agentDataCollectionInfo);
        }

        return agentChallengeDataCollectionInfo;
    }
}
