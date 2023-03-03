using Azure.Storage.Blobs;
using Microsoft.Azure.Cosmos;
using Microsoft.Extensions.Options;
using Newtonsoft.Json;
using PlaiGround.Api.Options;
using PlaiGround.Api.Utilities;
using System.Text;

namespace PlaiGround.Api.Services;

public class AgentChallengesService : IAgentChallengesService
{
    public const string ContainerName = "AgentChallenges";

    private readonly ILogger<AgentChallengesService> _logger;
    private readonly Container _cosmosContainer;
    private readonly BlobContainerClient _blobContainerClient;
    private readonly IEventsService _eventsService;
    private readonly IAgentsService _agentsService;

    public AgentChallengesService(
        ILogger<AgentChallengesService> logger,
        IOptions<CosmosOptions> cosmosOptions,
        CosmosClient cosmosClient,
        IOptions<StorageAccountOptions> storageAccountOptions,
        BlobServiceClient blobServiceClient,
        IEventsService eventsService,
        IAgentsService agentsService
    )
    {
        _logger = logger;
        _cosmosContainer = cosmosClient.GetContainer(cosmosOptions.Value.DatabaseName, ContainerName);
        _blobContainerClient = blobServiceClient.GetBlobContainerClient(storageAccountOptions.Value.AgentChallengeDataContainerName);
        _eventsService = eventsService;
        _agentsService = agentsService;
    }

    public async Task<IList<AgentChallenge>> GetAll()
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName}");
        var agentChallenges = await CosmosUtilities.ReadAll<AgentChallenge>(_cosmosContainer, query, _logger);

        return agentChallenges;
    }

    public async Task<IList<AgentChallenge>> GetManyByTournamentId(string tournamentId)
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName} ac WHERE ac.tournamentId = @tournamentId")
            .WithParameter("@tournamentId", tournamentId);
        var agentChallenges = await CosmosUtilities.ReadAll<AgentChallenge>(_cosmosContainer, query, _logger);

        return agentChallenges;
    }

    public async Task<AgentChallenge?> FindById(string tournamentId, string agentChallengeId)
    {
        var agentChallenge = await CosmosUtilities.FindItem<AgentChallenge>(_cosmosContainer, agentChallengeId, new PartitionKey(tournamentId));
        if (agentChallenge == null)
        {
            return default;
        }

        return agentChallenge;
    }

    public async Task<AgentChallenge> Add(AgentChallengeInput agentChallengeInput)
    {
        var agentChallenge = new AgentChallenge(agentChallengeInput);
        var agentChallengeResource = await _cosmosContainer.CreateItemAsync(agentChallenge, new PartitionKey(agentChallenge.TournamentId));

        return agentChallengeResource;
    }

    public async Task<AgentChallengeData> GetAgentChallengeData(AgentChallenge agentChallenge, ISet<string> agentServiceIds)
    {
        return agentChallenge.DownloadUrl == null
            ? await GetAgentChallengeDataFromCosmos(agentChallenge, agentServiceIds)
            : await GetAgentChallengeDataFromBlob(agentChallenge);
    }

    private async Task<AgentChallengeData> GetAgentChallengeDataFromBlob(AgentChallenge agentChallenge)
    {
        var blobName = GetBlobName(agentChallenge.Id);
        var blobClient = _blobContainerClient.GetBlobClient(blobName);
        var blob = await blobClient.DownloadContentAsync();
        var agentChallengeData = blob.Value.Content.ToObjectFromJson<AgentChallengeData>();

        return agentChallengeData;
    }

    private async Task<AgentChallengeData> GetAgentChallengeDataFromCosmos(AgentChallenge agentChallenge, ISet<string> agentServiceIds)
    {
        var groupId = GetGroupId(agentChallenge.TournamentId, agentChallenge.Id);
        var events = await _eventsService.GetEventsFromCosmos(agentChallenge.TournamentId, groupId);
        var agentChallengeData = AgentChallengeData.FromEvents(agentChallenge, agentServiceIds, events);

        return agentChallengeData;
    }

    private string GetGroupId(string tournamentId, string agentChallengeId)
    {
        return $"{tournamentId}:ac:{agentChallengeId}";
    }

    private string GetBlobName(string agentChallengeId)
    {
        return $"AgentChallengData-{agentChallengeId}.json";
    }

    private async Task<Uri> SaveData(AgentChallenge agentChallenge, ChallengeData agentChallengeData)
    {
        var blobName = GetBlobName(agentChallenge.Id);
        var agentChallengeDataJsonString = JsonConvert.SerializeObject(agentChallengeData, ChallengeData.SerializerSettings);
        var blobStream = new MemoryStream(Encoding.UTF8.GetBytes(agentChallengeDataJsonString));
        var blobClient = _blobContainerClient.GetBlobClient(blobName);
        await blobClient.UploadAsync(blobStream);

        return blobClient.Uri;
    }

    /// <summary>
    /// Calling end on Agent Challenge performs 5 steps:
    /// 1. Gather data
    /// 2. Save data
    /// 3. Set download url on challenge
    /// 4. Mark challenge as ended
    /// 5. Save updated challenge to Cosmos
    /// </summary>
    public async Task<AgentChallenge> End(string tournamentId, string agentChallengeId)
    {
        var agentChallengeResource = await CosmosUtilities.FindItem<AgentChallenge>(_cosmosContainer, agentChallengeId, new PartitionKey(tournamentId));
        if (agentChallengeResource == null)
        {
            throw new KeyNotFoundException($"Agent Challenge Code: {agentChallengeId} is not valid");
        }

        var agentChallenge = agentChallengeResource.Resource;

        // If challenge is not already ended, end it.
        if (agentChallenge.State != AgentChallengeState.Ended)
        {
            agentChallenge.State = AgentChallengeState.Ended;
            agentChallenge.Ended = DateTimeOffset.Now.UtcDateTime;

            var agentServices = await _agentsService.Get(agentChallengeId);
            var agentServiceIds = agentServices.Select(a => a.Id).ToHashSet();

            var agentChallengeData = await GetAgentChallengeData(agentChallenge, agentServiceIds);
            var downloadUrl = await SaveData(agentChallenge, agentChallengeData);
            agentChallenge.DownloadUrl = downloadUrl;

            agentChallengeResource = await _cosmosContainer.UpsertItemAsync(agentChallenge, new PartitionKey(agentChallenge.TournamentId));
        }

        return agentChallengeResource;
    }

    public async Task<AgentChallenge> UpdateDataCollectionInfo(string tournamentId, string agentChallengeId)
    {
        var groupId = GetGroupId(tournamentId, agentChallengeId);
        var events = await _eventsService.GetEventsFromCosmos(tournamentId, groupId);

        var agentServices = await _agentsService.Get(agentChallengeId);
        var agentServiceIds = agentServices.Select(a => a.Id).ToHashSet();
        var agentChallengeDataCollectionInfo = AgentChallengeDataCollectionInfo.FromEvents(agentServiceIds, events);

        var agentChallengeResource = await _cosmosContainer.ReadItemAsync<AgentChallenge>(agentChallengeId, new PartitionKey(tournamentId));
        var agentChallenge = agentChallengeResource.Resource;
        agentChallenge.DataCollectionInfo = agentChallengeDataCollectionInfo;

        agentChallengeResource = await _cosmosContainer.UpsertItemAsync(agentChallenge, new PartitionKey(agentChallenge.TournamentId));

        return agentChallengeResource;
    }

    public async Task Delete(string tournamentId, string agentChallengeId)
    {
        var blobName = GetBlobName(agentChallengeId);
        var blobClient = _blobContainerClient.GetBlobClient(blobName);
        await blobClient.DeleteIfExistsAsync();

        await _cosmosContainer.DeleteItemAsync<AgentChallenge>(agentChallengeId, new PartitionKey(tournamentId));
    }
}
