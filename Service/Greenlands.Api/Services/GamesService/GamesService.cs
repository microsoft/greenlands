using Azure.Storage.Blobs;
using Microsoft.Azure.Cosmos;
using Microsoft.Extensions.Options;
using Newtonsoft.Json;
using Greenlands.Api.Options;
using Greenlands.Api.Utilities;
using System.Text;

namespace Greenlands.Api.Services;

public class GamesService : IGamesService
{
    public const string ContainerName = "Games";

    private readonly ILogger<GamesService> _logger;
    private readonly ITasksService _tasksService;
    private readonly ITournamentsService _tournamentsService;
    private readonly IEventsService _eventsService;
    private readonly IEvaluationService _evaluationService;
    private readonly Container _cosmosContainer;
    private readonly BlobContainerClient _blobContainerClient;

    public GamesService(
        ILogger<GamesService> logger,
        IOptions<CosmosOptions> cosmosOptions,
        IOptions<EventHubOptions> eventHubOptions,
        CosmosClient cosmosClient,
        ITasksService tasksService,
        ITournamentsService tournamentsService,
        IEventsService eventsService,
        IEvaluationService evaluationService,
        IOptions<StorageAccountOptions> storageAccountOptions,
        BlobServiceClient blobServiceClient
    )
    {
        _logger = logger;
        _tasksService = tasksService;
        _tournamentsService = tournamentsService;
        _eventsService = eventsService;
        _evaluationService = evaluationService;
        _cosmosContainer = cosmosClient.GetContainer(cosmosOptions.Value.DatabaseName, ContainerName);
        _blobContainerClient = blobServiceClient.GetBlobContainerClient(storageAccountOptions.Value.GameDataContainerName);
    }

    public async Task<IList<Game>> Get(string taskId)
    {
        var query = new QueryDefinition($"SELECT * FROM {ContainerName} g WHERE g.taskId = @taskId")
            .WithParameter("@taskId", taskId);
        var games = await CosmosUtilities.ReadAll<Game>(_cosmosContainer, query, _logger);

        return games;
    }

    public async Task<Game?> Find(string taskId, string gameId)
    {
        var game = await CosmosUtilities.FindItem<Game>(_cosmosContainer, gameId, new PartitionKey(taskId));
        if (game == null)
        {
            return default;
        }

        return game;
    }

    public async Task<Game> Create(string tournamentId, string taskId)
    {
        var task = await _tasksService.Find(tournamentId, taskId);
        if (task == null)
        {
            throw new KeyNotFoundException($"You attempted to create a game with task id {taskId} but a task with that id does not exist.");
        }

        var game = new Game(task.Id);

        var gameResponse = await _cosmosContainer.CreateItemAsync(game, new PartitionKey(game.TaskId));

        return gameResponse;
    }

    public async Task<Game> Update(string taskId, string gameId, GameUpdate gameUpdate)
    {
        var gameFindResponse = await _cosmosContainer.ReadItemAsync<Game>(gameId, new PartitionKey(taskId));
        var game = gameFindResponse.Resource;

        if (gameUpdate.CompletionType != null)
        {
            game.CompletionType = gameUpdate.CompletionType;
        }

        var replaceResponse = await _cosmosContainer.ReplaceItemAsync(game, game.Id, new PartitionKey(game.TaskId));
        return replaceResponse;
    }

    public async Task<Game> Delete(string taskId, string gameId)
    {
        var game = await _cosmosContainer.DeleteItemAsync<Game>(gameId, new PartitionKey(taskId));

        return game;
    }

    public async Task<IList<BaseEvent>> GetEvents(string gameId)
    {
        var events = await _eventsService.GetEventsFromCosmos(gameId);

        return events;
    }

    public async Task<Uri> Save(string taskId, string gameId)
    {
        var events = await _eventsService.GetEventsFromCosmos(gameId);
        if (!events.Any())
        {
            throw new InvalidOperationException($"You attempted to save data for game {gameId} but there were no events for that game");
        }

        _logger.LogInformation($"Found {events.Count} events for game: {gameId}");

        // Save events that occurred during the game into Blob storage
        var firstEvent = events.First();
        var blobName = GetGameDataBlobName(firstEvent);
        var gameData = new GameData
        {
            Id = gameId,
            Events = events
        };
        var blobUri = await SaveGameData(blobName, gameData);
        _logger.LogInformation($"Saved game data to blob storage: {blobUri}");

        // Update the game in cosmos with the url of the blob
        var gameResponse = await _cosmosContainer.ReadItemAsync<Game>(gameId, new PartitionKey(taskId));
        var game = gameResponse.Resource;
        game.GameDataBlobUri = blobUri;
        var updatedGameResource = await _cosmosContainer.UpsertItemAsync(game, new PartitionKey(taskId));
        _logger.LogInformation($"Updated Game object with blob url");

        return updatedGameResource.Resource.GameDataBlobUri;
    }

    private string GetGameDataBlobName(BaseEvent firstEvent)
    {
        var blobName = $"tournaments/{firstEvent.TournamentId}";

        if (!string.IsNullOrEmpty(firstEvent.GroupId))
        {
            blobName += $"/groupId/{firstEvent.GroupId}";
        }

        blobName += $"/tasks/{firstEvent.TaskId}/games/{firstEvent.GameId}.json";

        return blobName;
    }

    private async Task<Uri> SaveGameData(string blobName, GameData gameData)
    {
        var blobDataJsonString = JsonConvert.SerializeObject(gameData, ChallengeData.SerializerSettings);
        var blobStream = new MemoryStream(Encoding.UTF8.GetBytes(blobDataJsonString));
        var blobClient = _blobContainerClient.GetBlobClient(blobName);
        await blobClient.UploadAsync(blobStream);

        return blobClient.Uri;
    }

    public async Task<double> Evaluate(string tournamentId, string taskId, string gameId)
    {
        var game = await Find(taskId, gameId);
        if (game == null)
        {
            throw new KeyNotFoundException($"Game with id {gameId} not found.");
        }

        var task = await _tasksService.Find(tournamentId, game.TaskId);
        if (task == null)
        {
            throw new KeyNotFoundException($"Task with id {game.TaskId} not found.");
        }

        var tournament = await _tournamentsService.Find(task.TournamentId);
        if (tournament == null)
        {
            throw new KeyNotFoundException($"Tournament with id {task.TournamentId} not found.");
        }

        var tournamentEvaluators = new Dictionary<EvaluatorIds, bool>
        {
            { EvaluatorIds.MatchWorldBlocksEvaluator, tournament.UseMatchedWorldBlocksEvaluator },
            { EvaluatorIds.MatchPlayerInventoryEvaluator, tournament.UseMatchedPlayerInventoryEvaluator },
            { EvaluatorIds.MatchPlayerLocationEvaluator, tournament.UseMatchedPlayerLocationEvaluator },
            { EvaluatorIds.MatchPlayerHoldEvaluator, tournament.UseMatchedPlayerHoldEvaluator },
            { EvaluatorIds.AnsweredQuestionEvaluator, tournament.UseAnsweredQuestionEvaluator },
            { EvaluatorIds.PlayerConfirmationEvaluator, tournament.UsePlayerConfirmationEvaluator },
        };

        var evaluations = new Dictionary<EvaluatorIds, double>();

        GameChanges? currentGameChanges = null;

        foreach (var (evaluatorId, useEvaluator) in tournamentEvaluators)
        {
            if (useEvaluator)
            {
                var eventsForGame = await _eventsService.GetEventsFromCosmos(gameId);

                currentGameChanges ??= await GetTotalGameChanges(eventsForGame);

                var evaluatorInput = _evaluationService.GetEvaluationInput(evaluatorId, tournament, task);
                var initialGameState = await _tasksService.GetInitialGameState(task);
                var targetGameChanges = await _tasksService.GetTargetGameChanges(task);
                var score = await _evaluationService.Evaluate(
                    evaluatorId,
                    evaluatorInput,
                    targetGameChanges,
                    currentGameChanges,
                    initialGameState
                );

                evaluations[evaluatorId] = score;
            }
        }

        var avgScore = evaluations.Values.Average();

        return avgScore;
    }

    /// <summary>
    /// Given game, return game changes which is accumulated changes from each turn of the game.
    /// </summary>
    public Task<GameChanges> GetTotalGameChanges(IList<BaseEvent> gameEvents)
    {
        var allGameChanges = new GameChanges
        {
            WorldChanges = new WorldChanges
            {
                BlockChanges = new Dictionary<LocationString, Block>()
            },
            PlayerChanges = new Dictionary<RoleId, PlayerChanges>()
        };

        // Compute game changes from events
        foreach (var baseEvent in gameEvents)
        {
            switch (baseEvent.EventType)
            {
                case nameof(BlockPlaceEvent):
                    {
                        var blockPlaceEvent = (BlockPlaceEvent)baseEvent;

                        // Set location to block in dictionary
                        allGameChanges.WorldChanges.BlockChanges[blockPlaceEvent.Location.ToString()] = new Block(blockPlaceEvent.Material);

                        break;
                    }
                // When blocks are broken/mined that location is replaced with a block of air
                case nameof(BlockRemoveEvent):
                    {
                        var blockRemoveEvent = (BlockRemoveEvent)baseEvent;
                        // TODO: Investigate using material name AIR instead of material id
                        // The names are stable across Minecraft versions
                        var airMaterialId = 0;

                        // Set location to block in dictionary
                        allGameChanges.WorldChanges.BlockChanges[blockRemoveEvent.Location.ToString()] = new Block(airMaterialId);

                        break;
                    }
            }
        }

        return Task.FromResult(allGameChanges);
    }
}
