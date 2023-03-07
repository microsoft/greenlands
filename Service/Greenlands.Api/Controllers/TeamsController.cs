using Microsoft.AspNetCore.Mvc;
using Microsoft.Identity.Web;
using Greenlands.Api.Auth;
using Greenlands.Api.Services;

namespace Greenlands.Api.Controllers;

/// <summary>
/// Collection of endpoints related to functionality to managing Teams.
/// </summary>
[ApiVersion("1.0")]
[Route("api/v{v:apiVersion}/[controller]")]
[Produces("application/json")]
[ApiController]
public class TeamsController : ControllerBase
{
    private readonly ILogger<TeamsController> _logger;
    private readonly ITeamsService _teamsService;
    private readonly IOwningTeamMemberAuthorizationService _owningTeamMemberAuthorizationService;

    public TeamsController(
        ILogger<TeamsController> logger,
        ITeamsService teamsService,
        IOwningTeamMemberAuthorizationService owningTeamMemberAuthorizationService
    )
    {
        _logger = logger;
        _teamsService = teamsService;
        _owningTeamMemberAuthorizationService = owningTeamMemberAuthorizationService;
    }

    /// <summary>
    /// Returns a list of all the teams.
    /// </summary>
    [HttpGet(Name = "GetTeams")]
    [ProducesResponseType(typeof(IList<Team>), 200)]
    public async Task<ActionResult> Get()
    {
        var teams = await _teamsService.GetAll();

        return Ok(teams);
    }

    /// <summary>
    /// Returns a list of all the teams IDs the current user is a member of.
    /// </summary>
    [HttpGet("me", Name = "GetTeamMemberships")]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(IList<string>), 200)]
    public async Task<ActionResult> GetTeamsMemberships()
    {
        var userId = User.GetObjectId();
        if (string.IsNullOrEmpty(userId))
        {
            // This should only happen if the request gets past the Authenticated User requirement if using an API Key such as from Minecraft
            return Unauthorized($"You attempted to get the IDs of teams the current user is a member of; however, there is not an authenticated user. Please ensure you are logged in as a user.");
        }

        var teams = await _teamsService.GetMyTeams(userId);
        var teamIds = teams.Select(t => t.Id);

        return Ok(teamIds);
    }

    /// <summary>
    /// Returns the team with the given ID, if there is one.
    /// </summary>
    [HttpGet("{teamId}", Name = "GetTeamById")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(Team), 200)]
    public async Task<ActionResult> GetById(string teamId)
    {
        var team = await _teamsService.Find(teamId);
        if (team == null)
        {
            return NotFound($"Could not find team with id {teamId}");
        }

        return Ok(team);
    }

    /// <summary>
    /// Creates a new team.
    /// </summary>
    [HttpPost(Name = "CreateTeam")]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(Team), 201)]
    public async Task<ActionResult> CreateTeam([FromBody] TeamInput teamInput)
    {
        var userId = User.GetObjectId();
        if (string.IsNullOrEmpty(userId))
        {
            // This should only happen if the request gets past the Authenticated User requirement if using an API Key such as from Minecraft
            return Unauthorized($"You attempted to create a team which requires and authenticated user but there is not a user available. Please check that you are authenticated.");
        }

        var team = await _teamsService.Create(teamInput, userId);
        // Reset cache so newly created team may be associated with user.
        await _owningTeamMemberAuthorizationService.DeleteUserTeamsAndTournamentsCache(userId);

        var routeValues = new { teamId = team.Id };

        return CreatedAtAction(nameof(GetById), routeValues, team);
    }

    /// <summary>
    /// Removes the current user from the team with the specified ID supposing
    /// that the user is already a member of said team.
    /// </summary>
    [HttpPut("{teamId}/leave", Name = "LeaveTeam")]
    [ProducesResponseType(400)]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(Team), 200)]
    public async Task<ActionResult> Leave(string teamId)
    {
        var userId = User.GetObjectId();
        if (string.IsNullOrEmpty(userId))
        {
            return Unauthorized($"You attempted to leave team: {teamId}, but you are not authenticated. This operation requires the user be authenticated. Please log in and try again.");
        }

        Team updatedTeam;

        try {
            updatedTeam = await _teamsService.Leave(teamId, userId);
            await _owningTeamMemberAuthorizationService.DeleteUserTeamsAndTournamentsCache(userId);
        }
        catch (InvalidOperationException e)
        {
            return BadRequest(e.Message);
        }

        return Ok(updatedTeam);
    }

    /// <summary>
    /// Updates the specified team.
    /// </summary>
    [HttpPut("{teamId}", Name = "UpdateTeam")]
    [ProducesResponseType(500)]
    public Task Update(string teamId, [FromBody] TeamInput TeamInput)
    {
        throw new NotImplementedException();
    }

    /// <summary>
    /// Deletes the specified team.
    /// </summary>
    [HttpDelete("{teamId}", Name = "DeleteTeam")]
    [ProducesResponseType(401)]
    [ProducesResponseType(404)]
    [ProducesResponseType(204)]
    public async Task<ActionResult> Delete(string teamId)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeam(User, teamId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var team = await _teamsService.Find(teamId);
        if (team == null)
        {
            return NotFound($"You attempted to delete team with id {teamId} but a team with that id was not found.");
        }

        await _teamsService.Delete(team.Id);

        return NoContent();
    }
}
