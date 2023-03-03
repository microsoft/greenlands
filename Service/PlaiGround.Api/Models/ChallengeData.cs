using Newtonsoft.Json;
using Newtonsoft.Json.Serialization;

namespace PlaiGround.Api.Models;

public class HumanChallengeData : ChallengeData
{
    public HumanChallenge HumanChallenge { get; init; }

    public static HumanChallengeData FromEvents(HumanChallenge humanChallenge, IList<BaseEvent> events)
    {
        var challengeData = FromEvents(events);
        humanChallenge.DataCollectionInfo = ChallengeDataCollectionInfo.FromEvents(events);
        var humanChallengeData = new HumanChallengeData
        {
            HumanChallenge = humanChallenge,
            Tournaments = challengeData.Tournaments,
        };

        return humanChallengeData;
    }
}

public class AgentChallengeData : ChallengeData
{
    public AgentChallenge AgentChallenge { get; init; }

    public static AgentChallengeData FromEvents(AgentChallenge agentChallenge, ISet<string> agentServiceIds, IList<BaseEvent> events)
    {
        var challengeData = FromEvents(events);
        agentChallenge.DataCollectionInfo = AgentChallengeDataCollectionInfo.FromEvents(agentServiceIds, events);
        var agentChallengeData = new AgentChallengeData
        {
            AgentChallenge = agentChallenge,
            Tournaments = challengeData.Tournaments,
        };

        return agentChallengeData;
    }
}

public class ChallengeData
{
    [Required]
    public IList<TournamentData> Tournaments { get; init; } = new List<TournamentData>();

    public static readonly JsonSerializerSettings SerializerSettings = new JsonSerializerSettings
    {
        ContractResolver = new CamelCasePropertyNamesContractResolver(),
        // TODO: Consider removing indentation to reduce string size.
        Formatting = Formatting.Indented,
    };

    public static ChallengeData FromEvents(IList<BaseEvent> events)
    {
        // Group events by gameId, taskId, and tournamentId
        var groupedEvents = events
            .GroupBy(
                e => e.TournamentId,
                (tournamentId, tournamentEvents) =>
                {
                    return new TournamentData
                    {
                        Id = tournamentId,
                        Tasks = tournamentEvents.GroupBy(
                            e => e.TaskId,
                            (taskId, taskEvents) =>
                            {
                                return new TaskData
                                {
                                    Id = taskId,
                                    Games = taskEvents.GroupBy(
                                        e => e.GameId,
                                        (gameId, gameEvents) =>
                                        {
                                            return new GameData
                                            {
                                                Id = gameId,
                                                Events = gameEvents.ToList()
                                            };
                                        }).ToList()
                                };
                            }).ToList()
                    };
                });

        var humanChallengeData = new ChallengeData
        {
            Tournaments = groupedEvents.ToList()
        };

        return humanChallengeData;
    }
}

public class TournamentData
{
    [Required]
    public string Id { get; init; }

    [Required]
    public IList<TaskData> Tasks { get; init; } = new List<TaskData>();
}

public class TaskData
{
    [Required]
    public string Id { get; init; }

    [Required]
    public IList<GameData> Games { get; init; } = new List<GameData>();
}

public class GameData
{
    [Required]
    public string Id { get; init; }

    [Required]
    public IList<BaseEvent> Events { get; init; } = new List<BaseEvent>();
}
