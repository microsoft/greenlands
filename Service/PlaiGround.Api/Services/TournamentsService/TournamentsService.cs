using Microsoft.Azure.Cosmos;
using Microsoft.Extensions.Options;
using PlaiGround.Api.Options;
using PlaiGround.Api.Utilities;

namespace PlaiGround.Api.Services;

public class TournamentsService : ITournamentsService
{
    public const string ContainerName = "Tournaments";

    private readonly ILogger<TournamentsService> _logger;
    private readonly Container _cosmosContainer;

    public TournamentsService(
        ILogger<TournamentsService> logger,
        IOptions<CosmosOptions> cosmosOptions,
        CosmosClient cosmosClient
    )
    {
        _logger = logger;
        _cosmosContainer = cosmosClient.GetContainer(cosmosOptions.Value.DatabaseName, ContainerName);
    }

    public async Task<IList<Tournament>> GetAll()
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName}");
        var tournaments = await CosmosUtilities.ReadAll<Tournament>(_cosmosContainer, query, _logger);

        return tournaments;
    }

    public async Task<IList<string>> GetTournamentIdsOwnedByTeamIds(IList<string> teamIds)
    {
        var query = new QueryDefinition($"SELECT t.id FROM {ContainerName} t WHERE ARRAY_CONTAINS(@teamIds, t.teamId)")
            .WithParameter("@teamIds", teamIds);
        var tournamentsWithId = await CosmosUtilities.ReadAll<Tournament>(_cosmosContainer, query, _logger);
        var tournamentIds = tournamentsWithId.Select(t => t.Id).ToList();

        return tournamentIds;
    }

    public async Task<Tournament?> Find(string tournamentId)
    {
        var tournament = await CosmosUtilities.FindItem<Tournament>(_cosmosContainer, tournamentId, new PartitionKey(tournamentId));
        if (tournament == null)
        {
            return default;
        }

        return tournament;
    }

    public async Task<Tournament> Create(TournamentInput tournamentInput)
    {
        var tournament = new Tournament(tournamentInput);
        var tournamentResponse = await _cosmosContainer.CreateItemAsync(tournament, new PartitionKey(tournament.Id));

        return tournamentResponse;
    }

    public async Task<Tournament> Update(string tournamentId, TournamentUpdate tournamentUpdate)
    {
        var tournamentFindResponse = await _cosmosContainer.ReadItemAsync<Tournament>(tournamentId, new PartitionKey(tournamentId));
        var tournament = tournamentFindResponse.Resource;
        tournament.ApplyUpdate(tournamentUpdate);

        var tournamentReplaceResponse = await _cosmosContainer.ReplaceItemAsync(tournament, tournament.Id, new PartitionKey(tournament.Id));

        return tournamentReplaceResponse;
    }

    public async Task<Tournament> Remove(string tournamentId)
    {
        var tournament = await _cosmosContainer.DeleteItemAsync<Tournament>(tournamentId, new PartitionKey(tournamentId));

        return tournament;
    }
}
