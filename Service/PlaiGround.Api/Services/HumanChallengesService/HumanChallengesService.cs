using Azure.Storage.Blobs;
using Microsoft.Azure.Cosmos;
using Microsoft.Extensions.Options;
using Newtonsoft.Json;
using PlaiGround.Api.Options;
using PlaiGround.Api.Utilities;
using System.Text;

namespace PlaiGround.Api.Services;

public class HumanChallengesService : IHumanChallengesService
{
    public const string ContainerName = "HumanChallenges";

    private readonly ILogger<HumanChallengesService> _logger;
    private readonly Container _cosmosContainer;
    private readonly BlobContainerClient _blobContainerClient;
    private readonly IEventsService _eventsService;

    public HumanChallengesService(
        ILogger<HumanChallengesService> logger,
        IOptions<CosmosOptions> cosmosOptions,
        CosmosClient cosmosClient,
        IOptions<StorageAccountOptions> storageAccountOptions,
        BlobServiceClient blobServiceClient,
        IEventsService eventsService
    )
    {
        _logger = logger;
        _cosmosContainer = cosmosClient.GetContainer(cosmosOptions.Value.DatabaseName, ContainerName);
        _blobContainerClient = blobServiceClient.GetBlobContainerClient(storageAccountOptions.Value.HumanChallengeDataContainerName);
        _eventsService = eventsService;
    }

    public async Task<IList<HumanChallenge>> GetAll(HumanChallengeQueryOptions? options = null)
    {
        List<HumanChallenge> humanChallenges;

        if (options?.HumanChallengeIds != null && options.HumanChallengeIds.Any())
        {
            humanChallenges = (await FindManyByIds(options.HumanChallengeIds)).ToList();
        }
        else
        {
            var query = new QueryDefinition($"SELECT * FROM {ContainerName}");
            humanChallenges = await CosmosUtilities.ReadAll<HumanChallenge>(_cosmosContainer, query, _logger);
        }

        return humanChallenges;
    }

    public async Task<IList<HumanChallenge>> GetManyByTournamentId(string tournamentId)
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName} hc WHERE hc.tournamentId = @tournamentId")
            .WithParameter("@tournamentId", tournamentId);
        var humanChallenges = await CosmosUtilities.ReadAll<HumanChallenge>(_cosmosContainer, query, _logger);

        return humanChallenges;
    }

    public async Task<IList<HumanChallenge>> FindManyByIds(ISet<string> humanChallengeIds)
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName} hc WHERE ARRAY_CONTAINS(@humanChallengeIds, hc.id)")
            .WithParameter("@humanChallengeIds", humanChallengeIds);
        var humanChallenges = await CosmosUtilities.ReadAll<HumanChallenge>(_cosmosContainer, query, _logger);

        return humanChallenges;
    }

    public async Task<HumanChallenge?> FindById(string tournamentId, string humanChallengeId)
    {
        var humanChallenge = await CosmosUtilities.FindItem<HumanChallenge>(_cosmosContainer, humanChallengeId, new PartitionKey(tournamentId));
        if (humanChallenge == null)
        {
            return default;
        }

        return humanChallenge;
    }

    public async Task<HumanChallenge> Add(HumanChallengeInput humanChallengeInput)
    {
        var humanChallenge = new HumanChallenge(humanChallengeInput);
        var humanChallengeResource = await _cosmosContainer.CreateItemAsync(humanChallenge, new PartitionKey(humanChallenge.TournamentId));

        return humanChallengeResource;
    }

    public async Task<HumanChallengeData> GetHumanChallengeData(HumanChallenge humanChallenge)
    {
        return humanChallenge.DownloadUrl == null
            ? await GetHumanChallengeDataFromCosmos(humanChallenge)
            : await GetHumanChallengeDataFromBlob(humanChallenge);
    }

    private async Task<HumanChallengeData> GetHumanChallengeDataFromBlob(HumanChallenge humanChallenge)
    {
        var blobName = GetBlobName(humanChallenge.Id);
        var blobClient = _blobContainerClient.GetBlobClient(blobName);
        var blob = await blobClient.DownloadContentAsync();
        var humanChallengeData = blob.Value.Content.ToObjectFromJson<HumanChallengeData>();

        return humanChallengeData;
    }

    private async Task<HumanChallengeData> GetHumanChallengeDataFromCosmos(HumanChallenge humanChallenge)
    {
        var groupId = GetGroupId(humanChallenge.TournamentId, humanChallenge.Id);
        var events = await _eventsService.GetEventsFromCosmos(humanChallenge.TournamentId, groupId);
        var humanChallengeData = HumanChallengeData.FromEvents(humanChallenge, events);

        return humanChallengeData;
    }

    private string GetGroupId(string tournamentId, string humanChallengeId)
    {
        return $"{tournamentId}:hc:{humanChallengeId}";
    }

    private string GetBlobName(string humanChallengeId)
    {
        return $"HumanChallengData-{humanChallengeId}.json";
    }

    private async Task<Uri> SaveData(HumanChallenge humanChallenge, ChallengeData humanChallengeData)
    {
        var blobName = GetBlobName(humanChallenge.Id);
        var humanChallengeDataJsonString = JsonConvert.SerializeObject(humanChallengeData, ChallengeData.SerializerSettings);
        var blobStream = new MemoryStream(Encoding.UTF8.GetBytes(humanChallengeDataJsonString));
        var blobClient = _blobContainerClient.GetBlobClient(blobName);
        await blobClient.UploadAsync(blobStream);

        return blobClient.Uri;
    }

    /// <summary>
    /// Calling end on Human Challenge performs 5 steps:
    /// 1. Gather data
    /// 2. Save data
    /// 3. Set download url on challenge
    /// 4. Mark challenge as ended
    /// 5. Save updated challenge to Cosmos
    /// </summary>
    public async Task<HumanChallenge> End(string tournamentId, string humanChallengeId)
    {
        var humanChallengeResource = await CosmosUtilities.FindItem<HumanChallenge>(_cosmosContainer, humanChallengeId, new PartitionKey(tournamentId));
        if (humanChallengeResource == null)
        {
            throw new KeyNotFoundException($"Human Challenge Code: {humanChallengeId} is not valid");
        }

        var humanChallenge = humanChallengeResource.Resource;

        // If challenge is not already ended, end it.
        if (humanChallenge.State != HumanChallengeState.Ended)
        {
            humanChallenge.State = HumanChallengeState.Ended;
            humanChallenge.Ended = DateTimeOffset.Now.UtcDateTime;
            var humanChallengeData = await GetHumanChallengeData(humanChallenge);
            var downloadUrl = await SaveData(humanChallenge, humanChallengeData);
            humanChallenge.DownloadUrl = downloadUrl;

            humanChallengeResource = await _cosmosContainer.UpsertItemAsync(humanChallenge, new PartitionKey(humanChallenge.TournamentId));
        }

        return humanChallengeResource;
    }

    public async Task<HumanChallenge> UpdateDataCollectionInfo(string tournamentId, string humanChallengeId)
    {
        var groupId = GetGroupId(tournamentId, humanChallengeId);
        var events = await _eventsService.GetEventsFromCosmos(tournamentId, groupId);

        var challengeDataCollectionInfo = ChallengeDataCollectionInfo.FromEvents(events);

        var humanChallengeResource = await _cosmosContainer.ReadItemAsync<HumanChallenge>(humanChallengeId, new PartitionKey(tournamentId));
        var humanChallenge = humanChallengeResource.Resource;
        humanChallenge.DataCollectionInfo = challengeDataCollectionInfo;

        humanChallengeResource = await _cosmosContainer.UpsertItemAsync(humanChallenge, new PartitionKey(humanChallenge.TournamentId));

        return humanChallengeResource;
    }

    public async Task Delete(string tournamentId, string humanChallengeId)
    {
        var blobName = GetBlobName(humanChallengeId);
        var blobClient = _blobContainerClient.GetBlobClient(blobName);
        await blobClient.DeleteIfExistsAsync();

        await _cosmosContainer.DeleteItemAsync<HumanChallenge>(humanChallengeId, new PartitionKey(tournamentId));
    }
}
