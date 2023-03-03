using Microsoft.AspNetCore.Mvc;
using PlaiGround.Api.Auth;
using PlaiGround.Api.Services;

namespace PlaiGround.Api.Controllers;

/// <summary>
/// Collection of endpoints related to functionality to managing Human
/// Challenges. See <see cref="HumanChallenge"/> for more information. 
/// </summary>
[ApiVersion("1.0")]
[Route("api/v{v:apiVersion}/tournaments/{tournamentId}/challenges/human")]
[Produces("application/json")]
[ApiController]
public class HumanChallengesController : ControllerBase
{
    private readonly ILogger<HumanChallengesController> _logger;
    private readonly IHumanChallengesService _humanChallengesService;
    private readonly IEventsService _eventsService;
    private readonly IOwningTeamMemberAuthorizationService _owningTeamMemberAuthorizationService;

    public HumanChallengesController(
        ILogger<HumanChallengesController> logger,
        IHumanChallengesService humanChallengesService,
        IEventsService eventsService,
        IOwningTeamMemberAuthorizationService owningTeamMemberAuthorizationService
    )
    {
        _logger = logger;
        _humanChallengesService = humanChallengesService;
        _eventsService = eventsService;
        _owningTeamMemberAuthorizationService = owningTeamMemberAuthorizationService;
    }

    /// <summary>
    /// Returns a list of all the human challenges that we know of.
    /// </summary>
    [HttpGet("/api/v{v:apiVersion}/challenges/human", Name = "GetHumanChallenges")]
    [ProducesResponseType(typeof(IList<HumanChallenge>), 200)]
    public async Task<ActionResult> Get([FromQuery] List<string>? humanChallengeIds)
    {
        var queryOptions = new HumanChallengeQueryOptions
        {
            HumanChallengeIds = humanChallengeIds?.ToHashSet()
        };

        var humanChallenges = await _humanChallengesService.GetAll(queryOptions);

        return Ok(humanChallenges);
    }

    /// <summary>
    /// Returns a list of all the human challenges for a given tournament.
    /// </summary>
    [HttpGet(Name = "GetHumanChallengesByTournamentId")]
    [ProducesResponseType(typeof(IList<HumanChallenge>), 200)]
    public async Task<ActionResult> GetByTournamentId(string tournamentId)
    {
        var humanChallenges = await _humanChallengesService.GetManyByTournamentId(tournamentId);

        return Ok(humanChallenges);
    }

    /// <summary>
    /// Returns the human challenge with the given ID, if there is one.
    /// </summary>
    [HttpGet("{id}", Name = "GetHumanChallengeById")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(HumanChallenge), 200)]
    public async Task<ActionResult> GetById(string tournamentId, string id)
    {
        var humanChallenge = await _humanChallengesService.FindById(tournamentId, id);
        if (humanChallenge == null)
        {
            return NotFound($"Could not find human challenge with id {id}");
        }

        return Ok(humanChallenge);
    }

    /// <summary>
    /// Returns all the event data for this human challenge. This contains all
    /// the data, for all games, that was produced during the human challenge.
    /// </summary>
    [HttpGet("{id}/download", Name = "GetHumanChallengeData")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(HumanChallengeData), 200)]
    public async Task<ActionResult> GetData(string tournamentId, string id)
    {
        var humanChallenge = await _humanChallengesService.FindById(tournamentId, id);
        if (humanChallenge == null)
        {
            return NotFound($"Could not find human challenge with id {id}");
        }

        var humanChallengeData = await _humanChallengesService.GetHumanChallengeData(humanChallenge);

        return Ok(humanChallengeData);
    }

    /// <summary>
    /// Creates a new human challenge.
    /// </summary>
    [HttpPost(Name = "CreateHumanChallenge")]
    [ProducesResponseType(400)]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(HumanChallenge), 201)]
    public async Task<ActionResult> Post(string tournamentId, [FromBody] HumanChallengeInput humanChallengeInput)
    {
        if (!string.Equals(tournamentId, humanChallengeInput.TournamentId))
        {
            return BadRequest($"You attempted to create a human challenge for {tournamentId} but the tournament ID of request body {humanChallengeInput.TournamentId} did not match the tournament ID in the URL. Please try again.");
        }

        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var humanChallenge = await _humanChallengesService.Add(humanChallengeInput);

        var routeValues = new { tournamentId = tournamentId, id = humanChallenge.Id };

        return CreatedAtAction(nameof(GetById), routeValues, humanChallenge);
    }

    /// <summary>
    /// Updates the human challenge with the given ID.
    /// </summary>
    [HttpPut("{humanChallengeId}/updateDataCollectionInfo", Name = "UpdateHumanChallengeDataCollectionInfo")]
    [ProducesResponseType(401)]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(HumanChallenge), 200)]
    public async Task<ActionResult> UpdateDataCollectionInfo(string tournamentId, string humanChallengeId)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var humanChallenge = await _humanChallengesService.FindById(tournamentId, humanChallengeId);
        if (humanChallenge == null)
        {
            return NotFound($"Could not find human challenge with id {humanChallengeId}");
        }

        humanChallenge = await _humanChallengesService.UpdateDataCollectionInfo(tournamentId, humanChallengeId);

        return Ok(humanChallenge);
    }

    /// <summary>
    /// Deletes the human challenge with the given ID.
    /// </summary>
    [HttpDelete("{id}", Name = "DeleteHumanChallenge")]
    [ProducesResponseType(401)]
    [ProducesResponseType(204)]
    public async Task<ActionResult> Delete(string tournamentId, string id)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        await _humanChallengesService.Delete(tournamentId, id);

        return NoContent();
    }

    /// <summary>
    /// Marks the human challenge with the given ID as ended.
    /// </summary>
    [HttpPut("{id}/end", Name = "EndHumanChallenge")]
    [ProducesResponseType(401)]
    [ProducesResponseType(404)]
    [ProducesResponseType(204)]
    public async Task<ActionResult> End(string tournamentId, string id)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var humanChallenge = await _humanChallengesService.FindById(tournamentId, id);
        if (humanChallenge == null)
        {
            return NotFound($"Could not find human challenge with id {id}");
        }

        await _humanChallengesService.End(tournamentId, id);

        return NoContent();
    }
}
