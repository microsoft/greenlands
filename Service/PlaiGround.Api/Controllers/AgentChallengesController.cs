using Microsoft.AspNetCore.Mvc;
using PlaiGround.Api.Auth;
using PlaiGround.Api.Services;

namespace PlaiGround.Api.Controllers;

/// <summary>
/// Collection of endpoints related to functionality for agent challenges.
/// </summary>
[ApiVersion("1.0")]
[Route("api/v{v:apiVersion}/tournaments/{tournamentId}/challenges/agent")]
[Produces("application/json")]
[ApiController]
public class AgentChallengesController : ControllerBase
{
    private readonly ILogger<AgentChallengesController> _logger;
    private readonly IAgentChallengesService _agentChallengesService;
    private readonly IOwningTeamMemberAuthorizationService _owningTeamMemberAuthorizationService;

    public AgentChallengesController(
        ILogger<AgentChallengesController> logger,
        IAgentChallengesService agentChallengesService,
        IOwningTeamMemberAuthorizationService owningTeamMemberAuthorizationService
    )
    {
        _logger = logger;
        _agentChallengesService = agentChallengesService;
        _owningTeamMemberAuthorizationService = owningTeamMemberAuthorizationService;
    }

    /// <summary>
    /// Returns a list of all the agent challenges that we know of.
    /// </summary>
    [HttpGet("/api/v{v:apiVersion}/challenges/agent", Name = "GetAgentChallenges")]
    [ProducesResponseType(typeof(IList<AgentChallenge>), 200)]
    public async Task<ActionResult> Get()
    {
        var agentChallenges = await _agentChallengesService.GetAll();

        return Ok(agentChallenges);
    }

    /// <summary>
    /// Returns a list of all the agent challenges for a given tournament.
    /// </summary>
    [HttpGet(Name = "GetAgentChallengesByTournamentId")]
    [ProducesResponseType(typeof(IList<AgentChallenge>), 200)]
    public async Task<ActionResult> GetByTournamentId(string tournamentId)
    {
        var agentChallenges = await _agentChallengesService.GetManyByTournamentId(tournamentId);

        return Ok(agentChallenges);
    }

    /// <summary>
    /// Returns the agent challenge with the given ID, if there is one.
    /// </summary>
    [HttpGet("{id}", Name = "GetAgentChallengeById")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(AgentChallenge), 200)]
    public async Task<ActionResult> FindById(string tournamentId, string id)
    {
        var agentChallenge = await _agentChallengesService.FindById(tournamentId, id);
        if (agentChallenge == null)
        {
            return NotFound($"Could not find agent challenge with id {id}");
        }

        return Ok(agentChallenge);
    }

    /// <summary>
    /// Creates a new agent challenge.
    /// </summary>
    [HttpPost(Name = "CreateAgentChallenge")]
    [ProducesResponseType(400)]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(AgentChallenge), 201)]
    public async Task<ActionResult> Post(string tournamentId, [FromBody] AgentChallengeInput agentChallengeInput)
    {
        if (!string.Equals(tournamentId, agentChallengeInput.TournamentId))
        {
            return BadRequest($"You attempted to create an agent challenge for {tournamentId} but the tournament ID of request body {agentChallengeInput.TournamentId} did not match the tournament ID in the URL. Please try again.");
        }

        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeam(User, agentChallengeInput.TeamId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var agentChallenge = await _agentChallengesService.Add(agentChallengeInput);

        var routeValues = new { tournamentId = tournamentId, id = agentChallenge.Id };

        return CreatedAtAction(nameof(FindById), routeValues, agentChallenge);
    }

    /// <summary>
    /// Updates the agent challenge with the given ID.
    /// </summary>
    [HttpPut("{agentChallengeId}/updateDataCollectionInfo", Name = "UpdateAgentChallengeDataCollectionInfo")]
    [ProducesResponseType(401)]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(AgentChallenge), 200)]
    public async Task<ActionResult> UpdateDataCollectionInfo(string tournamentId, string agentChallengeId)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var agentChallenge = await _agentChallengesService.FindById(tournamentId, agentChallengeId);
        if (agentChallenge == null)
        {
            return NotFound($"Could not find agent challenge with id {agentChallengeId}");
        }

        agentChallenge = await _agentChallengesService.UpdateDataCollectionInfo(tournamentId, agentChallengeId);

        return Ok(agentChallenge);
    }

    /// <summary>
    /// Removes the agent challenge with the given ID.
    /// </summary>
    [HttpDelete("{id}", Name = "DeleteAgentChallenge")]
    [ProducesResponseType(401)]
    [ProducesResponseType(204)]
    public async Task<ActionResult> Delete(string tournamentId, string id)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        await _agentChallengesService.Delete(tournamentId, id);

        return NoContent();
    }

    /// <summary>
    /// Marks the Agent Challenge with the given ID as ended.
    /// </summary>
    [HttpPut("{id}/end", Name = "EndAgentChallenge")]
    [ProducesResponseType(401)]
    [ProducesResponseType(204)]
    public async Task<ActionResult> End(string tournamentId, string id)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var agentChallenge = await _agentChallengesService.FindById(tournamentId, id);
        if (agentChallenge == null)
        {
            return NotFound($"Could not find agent challenge with id {id}");
        }

        await _agentChallengesService.End(tournamentId, id);

        return NoContent();
    }
}
