using Azure.Messaging.EventHubs;
using Azure.Messaging.EventHubs.Consumer;
using Azure.Messaging.EventHubs.Producer;
using Microsoft.Azure.Cosmos;
using Microsoft.Azure.ServiceBus;
using Microsoft.Extensions.Options;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using PlaiGround.Api.Options;
using PlaiGround.Api.Utilities;

namespace PlaiGround.Api.Services;

public class EventsService : IEventsService, IDisposable
{
    public const string ContainerName = "Events";

    private readonly ILogger<EventsService> _logger;
    private readonly Container _cosmosContainer;
    private readonly IEventConversionService _eventConversionService;
    private bool _disposed;
    private readonly EventHubConsumerClient _eventHubConsumer;
    private readonly EventHubProducerClient _eventHubProducer;

    public EventsService(
        ILogger<EventsService> logger,
        IOptions<CosmosOptions> cosmosOptions,
        CosmosClient cosmosClient,
        IOptions<EventHubOptions> eventHubOptions,
        IEventConversionService eventConversionService
    )
    {
        _logger = logger;
        _cosmosContainer = cosmosClient.GetContainer(cosmosOptions.Value.DatabaseName, ContainerName);
        _eventConversionService = eventConversionService;

        var csStringBuilder = new ServiceBusConnectionStringBuilder(
            eventHubOptions.Value.FullyQualifiedNamespace,
            eventHubOptions.Value.EventHubName,
            eventHubOptions.Value.NamespaceSendListenSharedAccessKeyName,
            eventHubOptions.Value.NamespaceSendListenSharedAccessKey
        );

        var connectionString = csStringBuilder.GetNamespaceConnectionString();
        var eventHubName = eventHubOptions.Value.EventHubName;

        if (string.IsNullOrEmpty(eventHubOptions.Value.ConsumerGroup))
        {
            throw new ArgumentNullException($"You attempted to create an EventsService witout specifying the consumer group. Consumer group is required. Please check the service configuration.");
        }

        _eventHubConsumer = new EventHubConsumerClient(eventHubOptions.Value.ConsumerGroup, connectionString, eventHubName);
        _eventHubProducer = new EventHubProducerClient(connectionString, eventHubName);
    }

    public async Task<IList<BaseEvent>> GetEventsFromCosmos(
        string tournamentId,
        string groupId,
        IList<string>? taskIds = null,
        DateTime? afterDateTime = null)
    {
        // Note: Could not find correct syntax for using IN parameter. It seems to treat taskIds parameter as single value or requires manual expansion
        // Using alternate ARRAY_CONTAINS https://stackoverflow.com/questions/56694915/cosmos-db-in-clause-thru-rest-api
        // Docs: https://docs.microsoft.com/en-us/azure/cosmos-db/sql/sql-query-array-contains
        var queryText = $"SELECT * FROM {ContainerName} e WHERE e.tournamentId = @tournamentId AND e.groupId = @groupId";
        var parameters = new Dictionary<string, object>
        {
            { "@tournamentId", tournamentId },
            { "@groupId", groupId },
        };

        if (taskIds != null && taskIds.Count > 0)
        {
            queryText += " AND ARRAY_CONTAINS(@taskIds, e.taskId)";
            parameters.Add("@taskIds", taskIds);
        }

        if (afterDateTime != null)
        {
            queryText += " AND e.EventEnqueuedUtcTime >= @afterDateTime";
            parameters.Add("@afterDateTime", afterDateTime);
        }

        var query = new QueryDefinition(queryText);
        foreach (var (paramKey, paramValue) in parameters)
        {
            query.WithParameter(paramKey, paramValue);
        }

        var dynamicEvents = await CosmosUtilities.ReadAll<dynamic>(_cosmosContainer, query, _logger);
        var jObjectEvents = dynamicEvents.Select<dynamic, JObject>(dynamicEvent => ConvertDynamicToJObjectWithNoDateParsing(dynamicEvent));
        var typedEvents = jObjectEvents
            .Select(jObjectEvent => _eventConversionService.DeserializeEvent(jObjectEvent))
            .ToList();

        return typedEvents;
    }

    public async Task<IList<BaseEvent>> GetEventsFromCosmos(string gameId)
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName} e WHERE e.gameId = @gameId")
                .WithParameter("@gameId", gameId);

        var dynamicEvents = await CosmosUtilities.ReadAll<dynamic>(_cosmosContainer, query, _logger);
        var jObjectEvents = dynamicEvents.Select<dynamic, JObject>(dynamicEvent => ConvertDynamicToJObjectWithNoDateParsing(dynamicEvent));
        var typedEvents = jObjectEvents
            .Select(jObjectEvent => _eventConversionService.DeserializeEvent(jObjectEvent))
            .ToList();

        return typedEvents;
    }

    /// <summary>
    /// Manually load dynamic objects into JObject with DateParseHandling set to None to avoid
    /// interpretting strings which look like dates as date objects
    /// https://github.com/JamesNK/Newtonsoft.Json/issues/862#issuecomment-222406929
    /// </summary>
    private JObject ConvertDynamicToJObjectWithNoDateParsing(dynamic dynamicObject)
    {
        using JsonReader jsonReader = new JsonTextReader(new StringReader(Convert.ToString(dynamicObject)));
        jsonReader.DateParseHandling = DateParseHandling.None;

        var jObject = JObject.Load(jsonReader);

        return jObject;
    }

    public async Task<IList<BaseEvent>> GetEventsFromEventHub(string gameId)
    {
        var partitionEvents = await GetPartitionEventsFromEventHub(gameId);
        var events = partitionEvents.Select(partitionEvent =>
        {
            var typedEvent = _eventConversionService.DeserializeEvent(partitionEvent.Data);

            return typedEvent;
        }).ToList();

        return events;
    }

    private async Task<List<PartitionEvent>> GetPartitionEventsFromEventHub(
        string gameId,
        TimeSpan maxTime = default
    )
    {
        if (maxTime == default)
        {
            maxTime = TimeSpan.FromSeconds(5);
        }

        // https://github.com/Azure/azure-sdk-for-net/blob/main/sdk/eventhub/Azure.Messaging.EventHubs/samples/Sample05_ReadingEvents.md
        var eventsForGame = new List<PartitionEvent>();

        try
        {
            using CancellationTokenSource cancellationSource = new CancellationTokenSource();
            cancellationSource.CancelAfter(maxTime);

            await foreach (var partitionEvent in _eventHubConsumer.ReadEventsAsync(cancellationSource.Token))
            {
                var eventGameId = partitionEvent.Data.Properties["gameId"] as string;
                _logger.LogInformation($"Read event for game: {eventGameId} from partitionId: {partitionEvent.Partition.PartitionId} at {DateTimeOffset.Now.ToString("yyyy-MM-ddTHH:mm:ss.fffffffK")}");

                if (eventGameId == gameId)
                {
                    eventsForGame.Add(partitionEvent);
                }
            }
        }
        catch (TaskCanceledException)
        {
            _logger.LogWarning($"When reading events from EventHub the max time was reached and stopped early. There might be more events for given game: {gameId}");
        }

        return eventsForGame;
    }

    /// <summary>
    /// Given list of events, publish to Event Hub.
    /// Uses gameId of first event as partitionKey
    /// 
    /// See sample for documentation
    /// https://github.com/Azure/azure-sdk-for-net/blob/main/sdk/eventhub/Azure.Messaging.EventHubs/samples/Sample04_PublishingEvents.md#event-hub-producer-client
    /// </summary>
    public async Task PublishEvents(
        IList<BaseEvent> baseEvents,
        string partitionKey 
    )
    {
        var batchOptions = new CreateBatchOptions
        {
            PartitionKey = partitionKey
        };

        var eventBatch = await _eventHubProducer.CreateBatchAsync(batchOptions);
        var skippedEvents = new List<BaseEvent>();

        try
        {
            foreach (var baseEvent in baseEvents)
            {
                var serializedEvent = JsonConvert.SerializeObject(baseEvent);
                var eventData = new EventData(serializedEvent);
                eventData.Properties.Add("eventType", baseEvent.EventType);
                eventData.Properties.Add("tournamentId", baseEvent.TournamentId);
                eventData.Properties.Add("taskId", baseEvent.TaskId);
                eventData.Properties.Add("gameId", baseEvent.GameId);
                eventData.Properties.Add("id", baseEvent.Id);
                eventData.Properties.Add("source", baseEvent.Source);
                eventData.Properties.Add("groupId", baseEvent.GroupId ?? string.Empty);

                var isAdded = eventBatch.TryAdd(eventData);
                if (isAdded)
                {
                    _logger.LogInformation($"Event {baseEvent.Id} of type {baseEvent.EventType} added to batch.");
                    continue;
                }

                if (eventBatch.Count == 0)
                {
                    _logger.LogWarning($"Event {baseEvent.Id} of type {baseEvent.EventType} could not be added to the batch even though batch is empty. Event must be too large to be added. Skipping event.");
                    skippedEvents.Add(baseEvent);
                    continue;
                }

                _logger.LogInformation($"Event {baseEvent.Id} of type {baseEvent.EventType} was not added to batch and batch is NOT empty. There must not be sufficient space for event in batch. Sending batch of {eventBatch.Count} events and recreating new empty batch.");
                await _eventHubProducer.SendAsync(eventBatch);
                eventBatch = await _eventHubProducer.CreateBatchAsync(batchOptions);

                // Attempt to add the event which could not fit into full batch into the newly created batch
                isAdded = eventBatch.TryAdd(eventData);
                if (!isAdded)
                {
                    _logger.LogWarning($"Event {baseEvent.Id} of type {baseEvent.EventType} could not be added to the batch even though batch is empty. Event must be too large to be added. Skipping event.");
                    skippedEvents.Add(baseEvent);
                    continue;
                }
            }

            if (eventBatch.Count > 0)
            {
                _logger.LogInformation($"Sending {eventBatch.Count} messages as a single batch.");
                await _eventHubProducer.SendAsync(eventBatch);
            }

            if (skippedEvents.Count > 0)
            {
                var sentEventsCount = baseEvents.Count - skippedEvents.Count;
                throw new Exception($"Not all events were sent. ({sentEventsCount}/{baseEvents.Count})");
            }
        }
        catch (Exception ex)
        {
            _logger.LogError($"Error when creating & sending a batch of events: { ex.Message}");
        }
        finally
        {
            eventBatch?.Dispose();
        }
    }

    /// <summary>
    /// Disposal of event hub resources when service is disposed
    /// https://docs.microsoft.com/en-us/aspnet/core/fundamentals/dependency-injection?view=aspnetcore-6.0#disposal-of-services
    /// </summary>
    public void Dispose()
    {
        if (_disposed)
            return;

        _eventHubConsumer.DisposeAsync();
        _eventHubProducer.DisposeAsync();
        _disposed = true;
    }
}
