using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using PlaiGround.Api.Auth;
using PlaiGround.Api.Services;

namespace PlaiGround.Api.Controllers;

[ApiVersion("1.0")]
[Route("api/v{v:apiVersion}/[controller]")]
[Produces("application/json")]
[ApiController]
public class UsersController : ControllerBase
{
    private readonly ILogger<UsersController> _logger;
    private readonly IOwningTeamMemberAuthorizationService _owningTeamMemberAuthorizationService;

    public UsersController(
        ILogger<UsersController> logger,
        IOwningTeamMemberAuthorizationService owningTeamMemberAuthorizationService
    )
    {
        _logger = logger;
        _owningTeamMemberAuthorizationService = owningTeamMemberAuthorizationService;
    }

    [HttpGet("currentUser/ownershipIds", Name = "GetCurrentUserTeamsAndTournamentIds")]
    [ProducesResponseType(typeof(UserOwnershipData), 200)]
    public async Task<ActionResult> GetCurrentUserTeamsAndTournamentIds()
    {
        var teamIdsAndTournamentIds = await _owningTeamMemberAuthorizationService.GetUserTeamAndTournamentIds(User);

        return Ok(teamIdsAndTournamentIds);
    }

    [HttpGet("{userId}/ownershipIds", Name = "GetUserTeamsAndTournamentIds")]
    [ProducesResponseType(typeof(UserOwnershipData), 200)]
    public async Task<ActionResult> GetUserTeamsAndTournamentIds(string userId)
    {
        var teamIdsAndTournamentIds = await _owningTeamMemberAuthorizationService.GetUserTeamAndTournamentIds(userId);

        return Ok(teamIdsAndTournamentIds);
    }
}
