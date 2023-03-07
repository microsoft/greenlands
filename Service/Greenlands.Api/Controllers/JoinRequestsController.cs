using Microsoft.AspNetCore.Mvc;
using Microsoft.Identity.Web;
using Greenlands.Api.Auth;
using Greenlands.Api.Services;

namespace Greenlands.Api.Controllers;

/// <summary>
/// Collection of endpoints related to functionality to managing requests to
/// join Teams.
/// </summary>
[ApiVersion("1.0")]
[Route("api/v{v:apiVersion}/teams/{teamId}/[controller]")]
[Produces("application/json")]
[ApiController]
public class JoinRequestsController : ControllerBase
{
    private readonly ILogger<JoinRequestsController> _logger;
    private readonly ITeamsService _teamsService;
    private readonly IJoinRequestsService _joinRequestsService;
    private readonly IOwningTeamMemberAuthorizationService _owningTeamMemberAuthorizationService;

    public JoinRequestsController(
        ILogger<JoinRequestsController> logger,
        ITeamsService teamService,
        IJoinRequestsService joinRequestService,
        IOwningTeamMemberAuthorizationService owningTeamMemberAuthorizationService
    )
    {
        _logger = logger;
        _teamsService = teamService;
        _joinRequestsService = joinRequestService;
        _owningTeamMemberAuthorizationService = owningTeamMemberAuthorizationService;
    }

    /// <summary>
    /// Returns a list of all the join requests for the given team.
    /// </summary>
    [HttpGet(Name = "GetRequests")]
    [ProducesResponseType(typeof(IList<JoinRequest>), 200)]
    public async Task<ActionResult> Get(string teamId)
    {
        var joinRequests = await _joinRequestsService.Get(teamId);

        return Ok(joinRequests);
    }

    /// <summary>
    /// Returns the join request with the given ID, if there is one.
    /// </summary>
    [HttpGet("{joinRequestId}", Name = "FindRequestById")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(JoinRequest), 200)]
    public async Task<ActionResult> FindById(string teamId, string joinRequestId)
    {
        var joinRequest = await _joinRequestsService.Find(teamId, joinRequestId);
        if (joinRequest == null)
        {
            return NotFound($"You attempted to find a request by id {joinRequestId} but request with that id does not exist.");
        }

        return Ok(joinRequest);
    }

    /// <summary>
    /// Creates a new join request for the given team.
    /// </summary>
    [HttpPost(Name = "RequestJoin")]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(Team), 201)]
    public async Task<ActionResult> CreateRequest(string teamId)
    {
        var userId = User.GetObjectId();
        if (string.IsNullOrEmpty(userId))
        {
            return Unauthorized($"You requested to join team {teamId} which requires and authenticated user but there is not a user available. Please log in and try again.");
        }

        var joinRequestInput = new JoinRequestInput
        {
            TeamId = teamId,
            UserId = userId
        };

        var joinRequest = await _joinRequestsService.CreateRequest(joinRequestInput);

        var routeValues = new { teamId = teamId, joinRequestId = joinRequest.Id };

        return CreatedAtAction(nameof(FindById), routeValues, joinRequest);
    }

    /// <summary>
    /// Approves the join request with the given ID. This must be done by an
    /// existing member of the team.
    /// </summary>
    [HttpPut("{joinRequestId}/approve", Name = "ApproveRequest")]
    [ProducesResponseType(401)]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(Team), 200)]
    public async Task<ActionResult> Approve(string teamId, string joinRequestId)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeam(User, teamId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var joinRequest = await _joinRequestsService.Find(teamId, joinRequestId);
        if (joinRequest == null)
        {
            return NotFound($"You attempted to find a request by id {joinRequestId} but request with that id does not exist.");
        }

        // TODO: Should be atomic operation?
        await _joinRequestsService.Approve(teamId, joinRequest.Id);
        var updatedTeam = await _teamsService.Join(teamId, joinRequest.UserId);
        // Reset the auth data for the user which was just added to the team so it can be recreated on their next request
        await _owningTeamMemberAuthorizationService.DeleteUserTeamsAndTournamentsCache(joinRequest.UserId);

        return Ok(updatedTeam);
    }

    /// <summary>
    /// Rejects the join request with the given ID. This must be done by an
    /// existing member of the team.
    /// </summary>
    [HttpPut("{joinRequestId}/reject", Name = "RejectRequest")]
    [ProducesResponseType(401)]
    [ProducesResponseType(404)]
    [ProducesResponseType(204)]
    public async Task<ActionResult> Reject(string teamId, string joinRequestId)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeam(User, teamId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var joinRequest = await _joinRequestsService.Find(teamId, joinRequestId);
        if (joinRequest == null)
        {
            return NotFound($"You attempted to find a request by id {joinRequestId} but request with that id does not exist.");
        }

        await _joinRequestsService.Reject(teamId, joinRequest.Id);

        return NoContent();
    }
}
