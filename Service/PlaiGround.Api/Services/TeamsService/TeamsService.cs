using Microsoft.Azure.Cosmos;
using Microsoft.Extensions.Options;
using PlaiGround.Api.Options;
using PlaiGround.Api.Utilities;

namespace PlaiGround.Api.Services;

public class TeamsService : ITeamsService
{
    public const string ContainerName = "Teams";

    private readonly ILogger<TeamsService> _logger;
    private readonly Container _cosmosContainer;

    public TeamsService(
        ILogger<TeamsService> logger,
        IOptions<CosmosOptions> cosmosOptions,
        CosmosClient cosmosClient
    )
    {
        _logger = logger;
        _cosmosContainer = cosmosClient.GetContainer(cosmosOptions.Value.DatabaseName, ContainerName);
    }

    public async Task<IList<Team>> GetAll()
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName}");
        var teams = await CosmosUtilities.ReadAll<Team>(_cosmosContainer, query, _logger);

        return teams;
    }

    public async Task<IList<Team>> GetMyTeams(string userId)
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName} t WHERE ARRAY_CONTAINS(t.memberIds, @userId)")
            .WithParameter("@userId", userId);
        var teams = await CosmosUtilities.ReadAll<Team>(_cosmosContainer, query, _logger);

        return teams;
    }

    public async Task<Team> Join(string teamId, string userId)
    {
        var teamResponse = await _cosmosContainer.ReadItemAsync<Team>(teamId, new PartitionKey(teamId));
        var team = teamResponse.Resource;
        var isUserMember = team.MemberIds.Contains(userId);
        // If user is already a member of the team, return the team
        // Otherwise, add the user to the team
        // TODO: Should we throw here?
        if (isUserMember)
        {
            return teamResponse;
        }

        team.MemberIds.Add(userId);

        teamResponse = await _cosmosContainer.ReplaceItemAsync<Team>(team, team.Id);

        return teamResponse;
    }

    public async Task<Team> Leave(string teamId, string userId)
    {
        var teamResponse = await _cosmosContainer.ReadItemAsync<Team>(teamId, new PartitionKey(teamId));
        var team = teamResponse.Resource;

        // If user is a member of team
        if (team.MemberIds.Contains(userId))
        {
            // If user is the LAST member of the team, they cannot be removed
            if (team.MemberIds.Count == 1)
            {
                throw new InvalidOperationException($"You attempted to remove user id {userId} from team {teamId}. Teams must have at least 1 member and {userId} was the last member. They cannot be removed.");
            }

            team.MemberIds.Remove(userId);
            teamResponse = await _cosmosContainer.ReplaceItemAsync<Team>(team, team.Id);
        }

        return teamResponse;
    }

    public async Task<Team?> Find(string teamId)
    {
        var team = await CosmosUtilities.FindItem<Team>(_cosmosContainer, teamId, new PartitionKey(teamId));
        if (team == null)
        {
            return default;
        }

        return team;
    }

    public async Task<Team> Create(TeamInput teamInput, string userId)
    {
        var team = new Team(teamInput);
        team.MemberIds.Add(userId);

        var teamResource = await _cosmosContainer.CreateItemAsync(team, new PartitionKey(team.Id));

        return teamResource;
    }

    public async Task Delete(string teamId)
    {
        await _cosmosContainer.DeleteItemAsync<Team>(teamId, new PartitionKey(teamId));
    }
}
