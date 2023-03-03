using Microsoft.AspNetCore.Mvc;
using PlaiGround.Api.Auth;
using PlaiGround.Api.Services;

namespace PlaiGround.Api.Controllers;

/// <summary>
/// Collection of endpoints related to functionality to managing Games.
/// </summary>
[ApiVersion("1.0")]
[Route("api/v{v:apiVersion}/tournaments/{tournamentId}/tasks/{taskId}/[controller]")]
[Produces("application/json")]
[ApiController]
public class GamesController : ControllerBase
{
    private readonly ILogger<GamesController> _logger;
    private readonly IGamesService _gamesService;
    private readonly IOwningTeamMemberAuthorizationService _owningTeamMemberAuthorizationService;

    public GamesController(
        ILogger<GamesController> logger,
        IGamesService gamesService,
        IOwningTeamMemberAuthorizationService owningTeamMemberAuthorizationService
    )
    {
        _logger = logger;
        _gamesService = gamesService;
        _owningTeamMemberAuthorizationService = owningTeamMemberAuthorizationService;
    }

    /// <summary>
    /// Returns a list of all the games that we know of.
    /// </summary>
    [HttpGet(Name = "GetGames")]
    [ProducesResponseType(typeof(IList<Game>), 200)]
    public async Task<ActionResult> Get(string taskId)
    {
        var games = await _gamesService.Get(taskId);

        return Ok(games);
    }

    /// <summary>
    /// Returns the game with the given ID, if there is one.
    /// </summary>
    [HttpGet("{gameId}", Name = "GetGameById")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(Game), 200)]
    public async Task<ActionResult> GetById(string taskId, string gameId)
    {
        var game = await _gamesService.Find(taskId, gameId);
        if (game == null)
        {
            return NotFound($"You attempted to find a game by id {gameId} but game does not exist with that id.");
        }

        return Ok(game);
    }

    /// <summary>
    /// Creates a new game for the specified combination of tournament and task.
    /// </summary>
    [HttpPost(Name = "CreateGame")]
    [ProducesResponseType(400)]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(Game), 201)]
    public async Task<ActionResult> Create(
        string tournamentId,
        string taskId
    )
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var game = await _gamesService.Create(tournamentId, taskId);

        var routeValues = new
        {
            tournamentId = tournamentId,
            taskId = taskId,
            gameId = game.Id
        };

        return CreatedAtAction(nameof(GetById), routeValues, game);
    }

    /// <summary>
    /// Deletes the game with the given ID. Note that the way the game is
    /// resolved is by checking if there is a game with the given ID for the
    /// specified combination of tournament and task.
    /// </summary>
    [HttpDelete("{gameId}", Name = "DeleteGame")]
    [ProducesResponseType(401)]
    [ProducesResponseType(204)]
    public async Task<ActionResult> Delete(string tournamentId, string taskId, string gameId)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var game = await _gamesService.Delete(taskId, gameId);

        return NoContent();
    }

    /// <summary>
    /// Updates a game.
    /// </summary>
    [HttpPost("{gameId}", Name = "UpdateGame")]
    [ProducesResponseType(400)]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(Game), 200)]
    public async Task<ActionResult> Update(string tournamentId, string taskId, string gameId, [FromBody] GameUpdate gameUpdate)
    {
        if (gameUpdate == null)
        {
            return BadRequest($"You attempted to update game: {gameId} but data to update the game was not provided");
        }

        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var game = await _gamesService.Update(taskId, gameId, gameUpdate);

        return Ok(game);
    }

    /// <summary>
    /// Returns the events for the game with the given ID. This will only return
    /// events if the game has been marked as completed.
    /// </summary>
    [HttpPost("{gameId}/events", Name = "GetEvents")]
    [ProducesResponseType(typeof(IList<BaseEvent>), 200)]
    public async Task<ActionResult> GetEvents(string gameId)
    {
        var events = await _gamesService.GetEvents(gameId);

        return Ok(events);
    }

    /// <summary>
    /// Effectively "saves" (aka "finishes") the specified game. Events for a
    /// game in progress will live in Event Hub until the game is saved (this
    /// endpoint). The save process will go look at the events in Event Hub for
    /// this game and save them into persistent storage (Cosmos).
    /// </summary>
    [HttpPost("{gameId}/save", Name = "SaveGame")]
    [ProducesResponseType(typeof(string), 200)]
    public async Task<ActionResult> SaveGame(string taskId, string gameId)
    {
        var gameBlobUri = await _gamesService.Save(taskId, gameId);

        return Ok(gameBlobUri);
    }

    /// <summary>
    /// Gets an evaluation score that represents how well the game went. Note:
    /// this is not used right now. We rely on human evaluation instead. <see
    /// cref="GameCompletionType"/>
    /// </summary>
    [HttpPost("{gameId}/evalute", Name = "Evaluate")]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(double), 200)]
    public async Task<ActionResult> Evaluate(
        string tournamentId,
        string taskId,
        string gameId
    )
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var score = await _gamesService.Evaluate(tournamentId, taskId, gameId);

        return Ok(score);
    }
}
