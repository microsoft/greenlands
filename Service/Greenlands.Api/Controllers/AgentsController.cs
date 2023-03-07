using Microsoft.AspNetCore.Mvc;
using Microsoft.Identity.Web;
using Greenlands.Api.Auth;
using Greenlands.Api.Services;

namespace Greenlands.Api.Controllers;

/// <summary>
/// Collection of endpoints related to functionality to managing Agents.
/// </summary>
[ApiVersion("1.0")]
[Route("api/v{v:apiVersion}/[controller]")]
[Produces("application/json")]
[ApiController]
public class AgentsController : ControllerBase
{
    private readonly ILogger<AgentsController> _logger;
    private readonly IAgentsService _agentsService;
    private readonly IOwningTeamMemberAuthorizationService _owningTeamMemberAuthorizationService;

    public AgentsController(
        ILogger<AgentsController> logger,
        IAgentsService agentsService,
        IOwningTeamMemberAuthorizationService owningTeamMemberAuthorizationService
    )
    {
        _logger = logger;
        _agentsService = agentsService;
        _owningTeamMemberAuthorizationService = owningTeamMemberAuthorizationService;
    }

    /// <summary>
    /// Returns a list of all the agent services that we know of.
    /// </summary>
    [HttpGet(Name = "GetAgentServices")]
    [ProducesResponseType(typeof(IList<AgentService>), 200)]
    public async Task<ActionResult> Get([FromQuery] string? agentChallengeId)
    {
        var agentServices = await _agentsService.Get(agentChallengeId);

        return Ok(agentServices);
    }

    /// <summary>
    /// Returns the agent service with the given ID, if there is one.
    /// </summary>
    [HttpGet("{id}", Name = "FindAgentServiceById")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(AgentService), 200)]
    public async Task<ActionResult> FindById(string id)
    {
        var agentService = await _agentsService.Find(id);
        if (agentService == null)
        {
            return NotFound($"Could not find agent service with id {id}");
        }

        return Ok(agentService);
    }

    /// <summary>
    /// Returns the connection info for the agent service with the given ID, if there is one.
    /// </summary>
    [HttpGet("{id}/connectioninfo", Name = "GetAgentServiceConnectionInfo")]
    [ProducesResponseType(401)]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(AgentConnectionInfo), 200)]
    public async Task<ActionResult> GetConnectionInfo(string id)
    {
        var agentService = await _agentsService.Find(id);
        if (agentService == null)
        {
            return NotFound($"Could not find agent service with id {id}");
        }

        // If agent service is owned by team, check if current user is member of the team
        // Otherwise agent service must be owned by individual user, check that current user matches the owner user id
        if (!string.IsNullOrEmpty(agentService.OwningTeamId))
        {
            await _owningTeamMemberAuthorizationService.IsUserMemberOfTeam(
                User,
                agentService.OwningTeamId,
                $"You attempted to get connection info for agent service: {id} which is owned by team: {agentService.OwningTeamId}. Please request to join that team to access this information."
            );
        }
        else
        {
            var userId = User.GetObjectId();
            var isAgentServiceOwnedByCurrentUser = string.Equals(agentService.CreatedByUserId, userId, StringComparison.OrdinalIgnoreCase);
            if (!isAgentServiceOwnedByCurrentUser)
            {
                return Unauthorized($"You attempted to get connection info for agent service: {id} which is owned by another user: {agentService.CreatedByUserId}. You may not access connection info of another user's agent service.");
            }
        }

        var connectionInfo = await _agentsService.GetConnectionInfo(id);

        return Ok(connectionInfo);
    }

    /// <summary>
    /// Creates a new agent service for the specified AgentChallenge.
    /// </summary>
    [HttpPost(Name = "CreateAgentService")]
    [ProducesResponseType(400)]
    [ProducesResponseType(typeof(AgentService), 201)]
    public async Task<ActionResult> Post([FromBody] AgentServiceInput agentServiceInput)
    {
        var userId = User.GetObjectId();
        var agentService = await _agentsService.Add(userId, agentServiceInput);

        var routeValues = new { id = agentService.Id };

        return CreatedAtAction(nameof(FindById), routeValues, agentService);
    }

    /// <summary>
    /// Updates the agent service with the given ID. Note: This is not currently
    /// implemented.
    /// </summary>
    [HttpPut("{id}", Name = "UpdateAgentService")]
    [ProducesResponseType(500)]
    public Task Put(string id, [FromBody] AgentServiceInput agentServiceInput)
    {
        throw new NotImplementedException();
    }

    /// <summary>
    /// Deletes the agent service with the given ID.
    /// </summary>
    [HttpDelete("{id}", Name = "DeleteAgentService")]
    [ProducesResponseType(401)]
    [ProducesResponseType(404)]
    [ProducesResponseType(204)]
    public async Task<ActionResult> Delete(string id)
    {
        var agentService = await _agentsService.Find(id);
        if (agentService == null)
        {
            return NotFound($"You attempted to delete agent service with id {id} but an aent service with that id was not found.");
        }

        // If agent service is owned by team, check if current user is member of the team
        // Otherwise agent service must be owned by individual user, check that current user matches the owner user id
        if (!string.IsNullOrEmpty(agentService.OwningTeamId))
        {
            await _owningTeamMemberAuthorizationService.IsUserMemberOfTeam(
                User,
                agentService.OwningTeamId,
                $"You attempted to delete agent service: {id} which is owned by team: {agentService.OwningTeamId}. Please request to join that team to perform this operation."
            );
        }
        else
        {
            var userId = User.GetObjectId();
            var isAgentServiceOwnedByCurrentUser = string.Equals(agentService.CreatedByUserId, userId, StringComparison.OrdinalIgnoreCase);
            if (!isAgentServiceOwnedByCurrentUser)
            {
                return Unauthorized($"You attempted to delete agent service: {id} which is owned by another user: {agentService.CreatedByUserId}. You may not delete another users agent service.");
            }
        }

        await _agentsService.Delete(agentService.Id);

        return NoContent();
    }
}
