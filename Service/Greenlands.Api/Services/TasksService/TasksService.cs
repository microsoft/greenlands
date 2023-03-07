using Azure.Storage.Blobs;
using Microsoft.Azure.Cosmos;
using Microsoft.Extensions.Options;
using Newtonsoft.Json;
using Greenlands.Api.Models;
using Greenlands.Api.Options;
using Greenlands.Api.Utilities;
using System.Text;

namespace Greenlands.Api.Services;

public class TasksService : ITasksService
{
    public const string ContainerName = "Tasks";

    private readonly ILogger<TasksService> _logger;
    private readonly ITournamentsService _tournamentsService;
    private readonly Container _tasksCosmosContainer;
    private readonly BlobContainerClient _blobContainerClient;
    private readonly IAgentChallengesService _agentChallengesService;
    private readonly IHumanChallengesService _humanChallengesService;

    public TasksService(
        ILogger<TasksService> logger,
        IOptions<CosmosOptions> cosmosOptions,
        ITournamentsService tournamentsService,
        CosmosClient cosmosClient,
        IOptions<StorageAccountOptions> storageAccountOptions,
        BlobServiceClient blobServiceClient,
        IAgentChallengesService agentChallengeService,
        IHumanChallengesService humanChallengesService
    )
    {
        _logger = logger;
        _tournamentsService = tournamentsService;
        _tasksCosmosContainer = cosmosClient.GetContainer(cosmosOptions.Value.DatabaseName, ContainerName);
        _blobContainerClient = blobServiceClient.GetBlobContainerClient(storageAccountOptions.Value.TaskDataContainerName);
        _agentChallengesService = agentChallengeService;
        _humanChallengesService = humanChallengesService;
    }

    public async Task<IList<GreenlandsTask>> Get(string tournamentId, TaskQueryOptions? options = null)
    {
        List<GreenlandsTask> tasks;

        if (!string.IsNullOrEmpty(options?.AgentChallengeId))
        {
            var agentChallenge = await _agentChallengesService.FindById(tournamentId, options.AgentChallengeId);
            if (agentChallenge == null)
            {
                tasks = new List<GreenlandsTask>();
            }
            else
            {
                var humanChallenges = await _humanChallengesService.FindManyByIds(agentChallenge.HumanChallengeIds.ToHashSet());
                var taskIds = humanChallenges.SelectMany(hc => hc.TaskIds).ToHashSet();
                tasks = (await FindManyByIds(taskIds)).ToList();
            }
        }
        else if (options?.TaskIds != null && options.TaskIds.Any())
        {
            tasks = (await FindManyByIds(options.TaskIds)).ToList();
        }
        else
        {
            var query = new QueryDefinition($"SELECT * FROM {ContainerName} t WHERE t.tournamentId = @tournamentId")
                .WithParameter("@tournamentId", tournamentId);
            tasks = await CosmosUtilities.ReadAll<GreenlandsTask>(_tasksCosmosContainer, query, _logger);
        }

        return tasks;
    }

    public async Task<IList<GreenlandsTask>> FindManyByIds(ISet<string> taskIds)
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName} t WHERE ARRAY_CONTAINS(@taskIds, t.id)")
            .WithParameter("@taskIds", taskIds);
        var tasks = await CosmosUtilities.ReadAll<GreenlandsTask>(_tasksCosmosContainer, query, _logger);

        return tasks;
    }

    public async Task<GreenlandsTask?> Find(string tournamentId, string taskId, TaskQueryOptions? options)
    {
        var task = await CosmosUtilities.FindItem<GreenlandsTask>(_tasksCosmosContainer, taskId, new PartitionKey(tournamentId));
        if (task == null)
        {
            return default;
        }

        var loadBlobData = options?.LoadBlobData ?? false;
        if (!loadBlobData)
        {
            return task;
        }

        var initialGameStateBlobName = GetInitialGameStateBlobName(taskId);
        var initialGameStateBlobClient = _blobContainerClient.GetBlobClient(initialGameStateBlobName);
        var initialGameStateBlobContent = await initialGameStateBlobClient.DownloadContentAsync();
        var initialGameStateBlobContentString = initialGameStateBlobContent.Value.Content.ToString();
        var initialGameState = JsonConvert.DeserializeObject<GameState>(initialGameStateBlobContentString, ChallengeData.SerializerSettings);

        var targetGameChangesBlobName = GetTargetGameChangesBlobName(taskId);
        var targetGameChangesBlobClient = _blobContainerClient.GetBlobClient(targetGameChangesBlobName);
        var targetGameChangesBlobContent = await targetGameChangesBlobClient.DownloadContentAsync();
        var targetGameChangesBlobContentString = targetGameChangesBlobContent.Value.Content.ToString();
        var targetGameChanges = JsonConvert.DeserializeObject<IList<GameChanges>>(targetGameChangesBlobContentString, ChallengeData.SerializerSettings);

        var completeTask = new CompleteTask(task)
        {
            InitialGameState = initialGameState,
            TargetGameChanges = targetGameChanges,
        };

        return completeTask;
    }

    public async Task<GreenlandsTask> Add(string tournamentId, TaskInput taskInput)
    {
        var tournament = await _tournamentsService.Find(tournamentId);
        if (tournament == null)
        {
            throw new KeyNotFoundException($"You attempted to add a task to tournament with id {tournamentId}, but a tournament with that ID did not exist.");
        }

        var taskId = Guid.NewGuid().ToString();

        // TODO: Could skip since uploading with possibly incorrect generator string and without block data is not useful
        // Save default values blobs so that the file exists for other consumers
        var defaultInitialGameState = new GameState
        {
            WorldState = new WorldState
            {
                // TODO: Issue with attempting to initialize blob here. generator string is not available in TaskInput.
                GeneratorName = "CleanroomGenerator:.1|minecraft:grass_block"
            }
        };

        var initialGameStateBlobName = GetInitialGameStateBlobName(taskId);
        var initialGameStateJsonString = JsonConvert.SerializeObject(defaultInitialGameState, ChallengeData.SerializerSettings);
        var initialGameStateBlobStream = new MemoryStream(Encoding.UTF8.GetBytes(initialGameStateJsonString));
        var initialGameStateBlobClient = _blobContainerClient.GetBlobClient(initialGameStateBlobName);
        await initialGameStateBlobClient.UploadAsync(initialGameStateBlobStream);
        
        // Save default initial world complete blocks blob
        var defaultInitialWorlCompletedBlocks = new Dictionary<LocationString, Block>();
        var initialWorldCompleteBlocksJsonString = JsonConvert.SerializeObject(defaultInitialWorlCompletedBlocks, ChallengeData.SerializerSettings); ;
        var initialWorldCompleteBlocksBlobStream = new MemoryStream(Encoding.UTF8.GetBytes(initialWorldCompleteBlocksJsonString));
        var initialWorldCompleteBlocksBlobName = GetInitialWorldCompleteBlocksBlobName(taskId);
        var initialWorldCompleteBlocksBlobClient = _blobContainerClient.GetBlobClient(initialWorldCompleteBlocksBlobName);
        await initialWorldCompleteBlocksBlobClient.UploadAsync(initialWorldCompleteBlocksBlobStream);

        // Save default target game changes blob
        var defaultTargetGameChanges = new List<GameChanges>
        {
            new GameChanges
            {
                WorldChanges = new WorldChanges
                {
                    BlockChanges = new Dictionary<LocationString, Block>()
                }
            }
        };

        var targetGameChangesBlobName = GetTargetGameChangesBlobName(taskId);
        var targetGameChangesJsonString = JsonConvert.SerializeObject(defaultTargetGameChanges, ChallengeData.SerializerSettings);
        var targetGameChangesBlobStream = new MemoryStream(Encoding.UTF8.GetBytes(targetGameChangesJsonString));
        var targetGameChangesBlobClient = _blobContainerClient.GetBlobClient(targetGameChangesBlobName);
        await targetGameChangesBlobClient.UploadAsync(targetGameChangesBlobStream);

        var task = new GreenlandsTask(tournament.Id, taskInput)
        {
            // Overwrite with ID used for blobs
            Id = taskId,
            InitialGameStateBlobUri = initialGameStateBlobClient.Uri,
            InitialWorldCompleteBlocksBlobUri = initialWorldCompleteBlocksBlobClient.Uri,
            TargetGameChangesBlobUri = targetGameChangesBlobClient.Uri
        };

        var taskResponse = await _tasksCosmosContainer.CreateItemAsync(task, new PartitionKey(task.TournamentId));

        return taskResponse;
    }

    public async Task<GreenlandsTask> Update(GreenlandsTask task)
    {
        var tournament = await _tournamentsService.Find(task.TournamentId);
        if (tournament == null)
        {
            throw new KeyNotFoundException($"You attempted to add a task to tournament with id {task.TournamentId}, but a tournament with that ID did not exist.");
        }

        task.Updated = DateTimeOffset.UtcNow;

        var taskResponse = await _tasksCosmosContainer.ReplaceItemAsync(task, task.Id, new PartitionKey(task.TournamentId));

        return taskResponse;
    }

    public async Task<GreenlandsTask> Delete(string tournamentId, string taskId)
    {
        // Delete Task Data from Blob Storage
        var initialGameStateBlobName = GetInitialGameStateBlobName(taskId);
        var initialGameStateBlobClient = _blobContainerClient.GetBlobClient(initialGameStateBlobName);
        await initialGameStateBlobClient.DeleteAsync();

        var targetGameChangesBlobName = GetTargetGameChangesBlobName(taskId);
        var targetGameChangesBlobClient = _blobContainerClient.GetBlobClient(targetGameChangesBlobName);
        await targetGameChangesBlobClient.DeleteAsync();

        // Delete Task from Cosmos
        var task = await _tasksCosmosContainer.DeleteItemAsync<GreenlandsTask>(taskId, new PartitionKey(tournamentId));

        return task;
    }

    public async Task<GameState> GetInitialGameState(GreenlandsTask task)
    {
        if (task.InitialGameStateBlobUri != null && !Uri.IsWellFormedUriString(task.InitialGameStateBlobUri.ToString(), UriKind.Absolute))
        {
            throw new InvalidOperationException($"You attempted to get the initial game state of a task that did not have an initial block changes blob uri.");
        }

        using var httpClient = new HttpClient();
        var initialGameState = await httpClient.GetFromJsonAsync<GameState>(task.InitialGameStateBlobUri);
        if (initialGameState == null)
        {
            throw new HttpRequestException($"Unknown error occurred when attempting to download initial game state for task: {task} at: {task.InitialGameStateBlobUri}");
        }

        return initialGameState;
    }

    private static string GetInitialGameStateBlobName(string taskId)
    {
        return $"{taskId}/initialGameState.json";
    }

    private static string GetInitialWorldCompleteBlocksBlobName(string taskId)
    {
        return $"{taskId}/initialWorldCompleteBlocks.json";
    }

    public async Task<IList<GameChanges>> GetTargetGameChanges(GreenlandsTask task)
    {
        if (task.TargetGameChangesBlobUri != null && !Uri.IsWellFormedUriString(task.TargetGameChangesBlobUri.ToString(), UriKind.Absolute))
        {
            throw new InvalidOperationException($"You attempted to get the target block changes of a task that did not have an target block changes blob uri.");
        }

        using var httpClient = new HttpClient();
        var targetGameChanges = await httpClient.GetFromJsonAsync<IList<GameChanges>>(task.TargetGameChangesBlobUri);
        if (targetGameChanges == null)
        {
            throw new HttpRequestException($"Unknown error occurred when attempting to download target block changes for task: {task} at: {task.TargetGameChangesBlobUri}");
        }

        return targetGameChanges;
    }

    private static string GetTargetGameChangesBlobName(string taskId)
    {
        return $"{taskId}/targetGameChanges.json";
    }
}
