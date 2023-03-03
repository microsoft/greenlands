using Azure.Messaging.ServiceBus.Administration;
using Azure.Storage.Blobs;
using Microsoft.Azure.Cosmos;
using Microsoft.Azure.ServiceBus;
using Microsoft.Azure.ServiceBus.Primitives;
using Microsoft.Extensions.Options;
using PlaiGround.Api.Options;
using PlaiGround.Api.Utilities;

namespace PlaiGround.Api.Services;

public class AgentsService : IAgentsService
{
    public const string ContainerName = "AgentServices";

    private readonly ILogger<AgentsService> _logger;
    private readonly Container _cosmosContainer;
    private readonly IOptions<EventHubOptions> _eventHubOptions;
    private readonly BlobContainerClient _blobContainerClient;

    public AgentsService(
        ILogger<AgentsService> logger,
        IOptions<CosmosOptions> cosmosOptions,
        CosmosClient cosmosClient,
        IOptions<EventHubOptions> eventHubOptions,
        IOptions<StorageAccountOptions> storageAccountOptions,
        BlobServiceClient blobServiceClient
    )
    {
        _logger = logger;
        _cosmosContainer = cosmosClient.GetContainer(cosmosOptions.Value.DatabaseName, ContainerName);
        _eventHubOptions = eventHubOptions;
        _blobContainerClient = blobServiceClient.GetBlobContainerClient(storageAccountOptions.Value.TaskDataContainerName);
    }

    public async Task<IList<AgentService>> Get(string? agentChallengeId)
    {
        var queryText = $"SELECT * FROM {ContainerName}";
        var parameters = new Dictionary<string, object>();

        if (!string.IsNullOrEmpty(agentChallengeId))
        {
            queryText += " a WHERE a.agentChallengeId = @agentChallengeId";
            parameters.Add("@agentChallengeId", agentChallengeId);
        }

        var query = new QueryDefinition(queryText);
        foreach (var (paramKey, paramValue) in parameters)
        {
            query.WithParameter(paramKey, paramValue);
        }

        var agentServices = await CosmosUtilities.ReadAll<AgentService>(_cosmosContainer, query, _logger);

        return agentServices;
    }

    public async Task<AgentService?> Find(string agentServiceId)
    {
        var agentService = await CosmosUtilities.FindItem<AgentService>(_cosmosContainer, agentServiceId, new PartitionKey(agentServiceId));
        if (agentService == null)
        {
            return default;
        }

        return agentService;
    }

    public async Task<AgentConnectionInfo> GetConnectionInfo(string agentServiceId)
    {
        // TODO: Find out if there is a maximum allowed amount of time?
        // This is sufficiently large that it would not expire during agent running lifetime
        var tokenTimeToLive = TimeSpan.FromDays(365);

        // Get SAS for publishing AND subscribing to games EventHub
        var publishAndSubscribeSasTokenProvider = (SharedAccessSignatureTokenProvider)TokenProvider.CreateSharedAccessSignatureTokenProvider(
            _eventHubOptions.Value.EventHubSendListenSharedAccessKeyName,
            _eventHubOptions.Value.EventHubSendListenSharedAccessKey,
            tokenTimeToLive,
            TokenScope.Entity);

        var publishAndSubscribeResourceUri = new Uri($"https://{_eventHubOptions.Value.FullyQualifiedNamespace}");
        var publishAndSubscribeTokenAudienceUri = new Uri(publishAndSubscribeResourceUri, _eventHubOptions.Value.EventHubName);
        var publishAndSubscribeSecurityToken = await publishAndSubscribeSasTokenProvider.GetTokenAsync(publishAndSubscribeTokenAudienceUri.ToString(), TimeSpan.FromMinutes(1));
        var publishAndSubscribeCsBuilder = new ServiceBusConnectionStringBuilder(
            _eventHubOptions.Value.FullyQualifiedNamespace,
            _eventHubOptions.Value.EventHubName,
            publishAndSubscribeSecurityToken.TokenValue
        );

        var eventConnectionInfo = new AgentConnectionInfo
        {
            BlobStorageContainerUrl = _blobContainerClient.Uri,
            PublishAndSubscribeSharedAccessSignature = publishAndSubscribeSecurityToken.TokenValue,
            PublishAndSubscribeConnectionString = publishAndSubscribeCsBuilder.GetEntityConnectionString(),
        };

        return eventConnectionInfo;
    }

    public async Task<AgentService> Add(string userId, AgentServiceInput agentServiceInput)
    {
        var agentService = new AgentService(userId, agentServiceInput);

        var agentServiceResource = await _cosmosContainer.CreateItemAsync(agentService, new PartitionKey(agentService.Id));

        return agentServiceResource;
    }

    public async Task Delete(string agentServiceId)
    {
        await _cosmosContainer.DeleteItemAsync<AgentService>(agentServiceId, new PartitionKey(agentServiceId));
    }
}
