using Microsoft.Azure.Cosmos;
using Microsoft.Extensions.Options;
using Greenlands.Api.Options;
using Greenlands.Api.Utilities;

namespace Greenlands.Api.Services;

public class JoinRequestsService : IJoinRequestsService
{
    public const string ContainerName = "TeamJoinRequests";

    private readonly ILogger<JoinRequestsService> _logger;
    private readonly Container _cosmosContainer;

    public JoinRequestsService(
        ILogger<JoinRequestsService> logger,
        IOptions<CosmosOptions> cosmosOptions,
        CosmosClient cosmosClient
    )
    {
        _logger = logger;
        _cosmosContainer = cosmosClient.GetContainer(cosmosOptions.Value.DatabaseName, ContainerName);
    }

    public async Task<IList<JoinRequest>> Get(string teamId)
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName} jr WHERE jr.teamId = @teamId")
            .WithParameter("@teamId", teamId);
        var joinRequests = await CosmosUtilities.ReadAll<JoinRequest>(_cosmosContainer, query, _logger);

        return joinRequests;
    }

    public async Task<JoinRequest?> Find(string teamId, string joinRequestId)
    {
        var joinRequestResponse = await CosmosUtilities.FindItem<JoinRequest>(_cosmosContainer, joinRequestId, new PartitionKey(teamId));
        if (joinRequestResponse == null)
        {
            return default;
        }

        return joinRequestResponse;
    }

    public async Task<JoinRequest> CreateRequest(JoinRequestInput joinRequestInput)
    {
        var joinRequest = new JoinRequest(joinRequestInput);
        var joinRequestResponse = await _cosmosContainer.CreateItemAsync(joinRequest, new PartitionKey(joinRequest.TeamId));

        return joinRequestResponse;
    }

    public async Task Approve(string teamId, string joinRequestId)
    {
        await Delete(teamId, joinRequestId);
    }

    public async Task Reject(string teamId, string joinRequestId)
    {
        await Delete(teamId, joinRequestId);
    }

    private async Task Delete(string teamId, string joinRequestId)
    {
        await _cosmosContainer.DeleteItemAsync<JoinRequest>(joinRequestId, new PartitionKey(teamId));
    }
}
