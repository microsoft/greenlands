using Microsoft.AspNetCore.Mvc;
using Microsoft.Identity.Web;
using PlaiGround.Api.Auth;
using PlaiGround.Api.Services;

namespace PlaiGround.Api.Controllers;

/// <summary>
/// Collection of endpoints related to functionality to managing Tournaments.
/// </summary>
[ApiVersion("1.0")]
[Route("api/v{v:apiVersion}/[controller]")]
[Produces("application/json")]
[ApiController]
public class TournamentsController : ControllerBase
{
    private readonly ILogger<TournamentsController> _logger;
    private readonly ITournamentsService _tournamentsService;
    private readonly IEvaluationService _evaluationService;
    private readonly IOwningTeamMemberAuthorizationService _owningTeamMemberAuthorizationService;

    public TournamentsController(
        ILogger<TournamentsController> logger,
        ITournamentsService tournamentsService,
        IEvaluationService evaluationService,
        IOwningTeamMemberAuthorizationService owningTeamMemberAuthorizationService
    )
    {
        _logger = logger;
        _tournamentsService = tournamentsService;
        _evaluationService = evaluationService;
        _owningTeamMemberAuthorizationService = owningTeamMemberAuthorizationService;
    }

    /// <summary>
    /// Returns a list of all the tournaments.
    /// </summary>
    [HttpGet(Name = "GetTournaments")]
    [ProducesResponseType(typeof(IList<Tournament>), 200)]
    public async Task<ActionResult> Get()
    {
        var tournaments = await _tournamentsService.GetAll();

        return Ok(tournaments);
    }

    /// <summary>
    /// Returns the tournament with the given ID.
    /// </summary>
    [HttpGet("{id}", Name = "GetTournamentById")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(Tournament), 200)]
    public async Task<ActionResult> GetById(string id)
    {
        var tournament = await _tournamentsService.Find(id);
        if (tournament == null)
        {
            return NotFound($"You attempted to find a Tournament by id {id} but tournament with that id does not exist.");
        }

        return Ok(tournament);
    }

    /// <summary>
    /// Creates a new tournament.
    /// </summary>
    [HttpPost(Name = "CreateTournament")]
    [ProducesResponseType(400)]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(Tournament), 201)]
    public async Task<ActionResult> Create([FromBody] TournamentInput tournamentInput)
    {
        // TODO: Replace with resource authorization handler
        // https://docs.microsoft.com/en-us/aspnet/core/security/authorization/resourcebased?view=aspnetcore-6.0#challenge-and-forbid-with-an-operational-resource-handler
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeam(User, tournamentInput.TeamId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var evaluatorsMap = tournamentInput.GetEvauatorsDictionary();
        var isAtLeastOneEvaluatorEnabled = evaluatorsMap.Values.Where(isEvaluatorEnabled => isEvaluatorEnabled).Count() >= 1;
        if (!isAtLeastOneEvaluatorEnabled)
        {
            // Mimic the same structure of error that would be returned by dotnet ModelValidation
            // TODO: Use native dotnet class to avoid duplication and divergence
            var clientErrorResponse = new ClientErrorResponse
            {
                Errors = new Dictionary<string, List<string>>
                {
                    ["EvaluatorOptions"] = new List<string>
                    {
                        $"You attempted to create a tournament with no evaluation metric specified. Please try again with at least one evaluator enabled."
                    }
                }
            };

            return BadRequest(clientErrorResponse);
        }

        var tournament = await _tournamentsService.Create(tournamentInput);
        var userId = User.GetObjectId();
        // Reset cache so newly created tournament may be associated with user.
        await _owningTeamMemberAuthorizationService.DeleteUserTeamsAndTournamentsCache(userId);

        var routeValues = new { id = tournament.Id };

        return CreatedAtAction(nameof(GetById), routeValues, tournament);
    }

    /// <summary>
    /// Updates the tournament with the given ID.
    /// </summary>
    [HttpPut("{tournamentId}", Name = "UpdateTournament")]
    [ProducesResponseType(400)]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(Tournament), 200)]
    public async Task<ActionResult> Put(string tournamentId, [FromBody] TournamentUpdate tournamentUpdate)
    {
        if (tournamentUpdate == null)
        {
            return BadRequest($"You attempted to update tournament: {tournamentId} but a tournament was not provided");
        }

        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var updatedTournament = await _tournamentsService.Update(tournamentId, tournamentUpdate);

        return Ok(updatedTournament);
    }

    /// <summary>
    /// Deletes the tournament with the given ID.
    /// </summary>
    [HttpDelete("{id}", Name = "DeleteTournament")]
    [ProducesResponseType(401)]
    [ProducesResponseType(404)]
    [ProducesResponseType(204)]
    public async Task<ActionResult> Delete(string id)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, id);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var tournament = await _tournamentsService.Find(id);
        if (tournament == null)
        {
            return NotFound($"You attempted to delete tournament with id {id}, but tournament with that id does not exist.");
        }

        await _tournamentsService.Remove(id);

        return NoContent();
    }
}
