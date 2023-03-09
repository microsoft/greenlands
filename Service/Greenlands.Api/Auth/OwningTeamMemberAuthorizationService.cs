using System.Diagnostics;
using System.Security.Claims;
using Greenlands.Api.Options;
using Greenlands.Api.Services;
using Microsoft.Extensions.Options;
using Microsoft.Identity.Web;
using StackExchange.Redis;

namespace Greenlands.Api.Auth;

public interface IOwningTeamMemberAuthorizationService
{
    public Task<InvalidOperationException?> IsUserMemberOfTeam(ClaimsPrincipal user, string teamId, string? operation = null);

    public Task<InvalidOperationException?> IsUserMemberOfTeamOwningTournament(ClaimsPrincipal user, string tournamentId, string? operation = null);

    public Task<UserOwnershipData> GetUserTeamAndTournamentIds(ClaimsPrincipal user);

    public Task<UserOwnershipData> GetUserTeamAndTournamentIds(string userId);

    public Task DeleteUserTeamsAndTournamentsCache(string userId);
}

public class OwningTeamMemberAuthorizationService : IOwningTeamMemberAuthorizationService
{
    private readonly ILogger<OwningTeamMemberAuthorizationService> _logger;
    private readonly IOptions<ApiKeyAuthenticationOptions> _apiKeyAuthOptions;
    private readonly ITeamsService _teamsService;
    private readonly ITournamentsService _tournamentsService;
    private readonly IOptions<RedisOptions> _redisOptions;
    private readonly ConnectionMultiplexer _connectionMultiplexer;

    public OwningTeamMemberAuthorizationService(
        IOptions<ApiKeyAuthenticationOptions> apiKeyAuthOptions,
        ILogger<OwningTeamMemberAuthorizationService> logger,
        ITeamsService teamsService,
        ITournamentsService tournamentsService,
        IOptions<RedisOptions> redisOptions,
        ConnectionMultiplexer connectionMultiplexer
    )
    {
        _logger = logger;
        _apiKeyAuthOptions = apiKeyAuthOptions;
        _teamsService = teamsService;
        _tournamentsService = tournamentsService;
        _redisOptions = redisOptions;
        _connectionMultiplexer = connectionMultiplexer;
    }

    public async Task<InvalidOperationException?> IsUserMemberOfTeam(
        ClaimsPrincipal user,
        string teamId,
        string? operation = null
    )
    {
        var canUserBypassAuthorization = CanUserBypassAuthorization(user);
        if (canUserBypassAuthorization)
        {
            return null;
        }

        var stopwatch = new Stopwatch();
        stopwatch.Start();

        string userId;
        List<string> teamIdsUserIsMemberOf;

        try
        {
            teamIdsUserIsMemberOf = await GetUserTeams(user);
            userId = user.GetObjectId()!;
        }
        catch (InvalidOperationException e)
        {
            return e;
        }

        var isUserMemberOfTeam = teamIdsUserIsMemberOf.Contains(teamId);
        if (!isUserMemberOfTeam)
        {
            operation ??= $"perform an operation on team: {teamId} that requires membership";
            var errorMessage = $"You attempted to {operation} but you are not a member of that team. You may not add or modify resources to this team. Please request to join the team.";
            _logger.LogError(errorMessage);
            return new InvalidOperationException(errorMessage);
        }

        stopwatch.Stop();
        _logger.LogInformation($"Checking if user {userId} is member of team {teamId} took {stopwatch.ElapsedMilliseconds} ms");

        return null;
    }

    /// <summary>
    /// Given user and tournamentId, if user is NOT a member of team which owns that tournament return InvalidOperationException
    /// Otherwise, return null
    /// 
    /// For example:
    /// TeamA has members: [User1, User2]
    /// Task1 belongs to Tournament1 which has teamId TeamA
    /// Hierarchy: TeamA -> Tournament1 -> Task1
    /// 
    /// Succeed:
    /// Scenario: User2 attempts to edit Task1
    /// Outcome: Tis operation is allowed because User2 is member of TeamA
    /// 
    /// Failure:
    /// Scenario: User3 attempts to edit Task1
    /// Outcome: This operation is rejected because User3 is NOT a member of TeamA
    /// </summary>
    public async Task<InvalidOperationException?> IsUserMemberOfTeamOwningTournament(
        ClaimsPrincipal user,
        string tournamentId,
        string? operationName = null
    )
    {
        var canUserBypassAuthorization = CanUserBypassAuthorization(user);
        if (canUserBypassAuthorization)
        {
            return null;
        }

        var stopwatch = new Stopwatch();
        stopwatch.Start();

        string userId;
        List<string> teamIdsUserIsMemberOf;

        try
        {
            teamIdsUserIsMemberOf = await GetUserTeams(user);
            userId = user.GetObjectId();
        }
        catch (InvalidOperationException e)
        {
            return e;
        }

        var tournamentIdsOwnedByTeamsUserIsMemberOf = await GetUserTournamentIds(userId, teamIdsUserIsMemberOf);
        var isTournamentOfRequestEditableByUser = tournamentIdsOwnedByTeamsUserIsMemberOf.Contains(tournamentId);
        if (!isTournamentOfRequestEditableByUser)
        {
            operationName ??= $"modify a resource of tournament {tournamentId}";
            var errorMessage = $"You attempted to {operationName} but you are not a member of the team which owns this tournament. You may not modify this resource. Please request to join the team.";
            _logger.LogError(errorMessage);
            return new InvalidOperationException(errorMessage);
        }

        stopwatch.Stop();
        _logger.LogInformation($"Checking if user {userId} is member of team owning tournament {tournamentId} took {stopwatch.ElapsedMilliseconds} ms");

        return null;
    }

    /// <summary>
    /// Given user, return the user's id and the IDs of teams and tournaments the user can manage
    /// </summary>
    public async Task<UserOwnershipData> GetUserTeamAndTournamentIds(ClaimsPrincipal user)
    {
        var canUserBypassAuthorization = CanUserBypassAuthorization(user);
        if (canUserBypassAuthorization)
        {
            return new UserOwnershipData();
        }

        var userId = user.GetObjectId();
        var teamAndTournamentIds = await GetUserTeamAndTournamentIds(userId);

        return teamAndTournamentIds;
    }

    /// <summary>
    /// Given userId, return the userId and the IDs of teams and tournaments the user can manage
    /// </summary>
    public async Task<UserOwnershipData> GetUserTeamAndTournamentIds(string userId)
    {
        var teamIdsUserIsMemberOf = await GetUserTeams(userId);
        var tournamentIdsOwnedByTeamsUserIsMemberOf = await GetUserTournamentIds(userId, teamIdsUserIsMemberOf);

        return new UserOwnershipData
        {
            UserId = userId,
            TeamIds = teamIdsUserIsMemberOf,
            TournamentIds = tournamentIdsOwnedByTeamsUserIsMemberOf,
        };
    }

    /// <summary>
    /// Deletes the Redis data for a given userId which effectively
    /// resets the mapping between a given userId and their team memberships and tournaments they can manage.
    /// </summary>
    public async Task DeleteUserTeamsAndTournamentsCache(string userId)
    {
        var db = _connectionMultiplexer.GetDatabase();

        var userTeamsKey = GetUserTeamsKey(userId);
        await db.KeyDeleteAsync(userTeamsKey);

        var userTournamentsKey = GetUserTournamentsKey(userId);
        await db.KeyDeleteAsync(userTournamentsKey);

        _logger.LogInformation($"Cleared team and tournament ids for user {userId}");
    }

    private string GetUserTeamsKey(string userId)
    {
        return $"{_redisOptions.Value.KeyPrefix}:{userId}:teamIds";
    }

    /// <summary>
    /// Given a user, return the ids of teams that user is a member of.
    /// First checks Redis, if not set, checks database
    /// </summary>
    private async Task<List<string>> GetUserTeams(ClaimsPrincipal user)
    {
        var canUserBypassAuthorization = CanUserBypassAuthorization(user);
        if (canUserBypassAuthorization)
        {
            return new List<string>();
        }

        var userId = user.GetObjectId();
        if (string.IsNullOrEmpty(userId))
        {
            throw new InvalidOperationException($"You attempted to get the ID of current user but it was an empty string or null");
        }

        return await GetUserTeams(userId);
    }

    private async Task<List<string>> GetUserTeams(string userId)
    {
        if (string.IsNullOrEmpty(userId))
        {
            var errorMessage = $"Authorization Failed! Attempted to get ID from user but it was empty. Please log in and try again.";
            _logger.LogWarning(errorMessage);
            throw new InvalidOperationException(errorMessage);
        }

        _logger.LogInformation($"User ID is {userId}");

        // Get team ids the user is a member of
        List<string> teamIdsUserIsMemberOf = null;

        // First attemptm to get values from Redis
        // If values not set, then get from database and write to Redis
        var db = _connectionMultiplexer.GetDatabase();
        var userTeamsKey = GetUserTeamsKey(userId);
        var doesUserTeamsKeyExist = await db.KeyExistsAsync(userTeamsKey);
        if (doesUserTeamsKeyExist)
        {
            var teamIdsAsRedisValues = await db.SetMembersAsync(userTeamsKey);
            if (teamIdsAsRedisValues != null && teamIdsAsRedisValues.Length > 0)
            {
                teamIdsUserIsMemberOf = teamIdsAsRedisValues.Select(val => val.ToString()).ToList();
                _logger.LogInformation($"User team ids found in Redis!");
            }
        }

        if (teamIdsUserIsMemberOf == null)
        {
            _logger.LogInformation($"User team ids NOT found in Redis. Retrieving from database");

            var teamsUserIsMemberOf = await _teamsService.GetMyTeams(userId);
            teamIdsUserIsMemberOf = teamsUserIsMemberOf.Select(t => t.Id).ToList();

            foreach (var teamid in teamIdsUserIsMemberOf)
            {
                await db.SetAddAsync(userTeamsKey, teamid);
            }

            await db.KeyExpireAsync(userTeamsKey, TimeSpan.FromMinutes(_redisOptions.Value.KeyExpirationMinutesNumber));
            _logger.LogInformation($"Saved team ids to Redis at key {userTeamsKey}");
        }

        _logger.LogInformation($"User is member of teams: {string.Join(", ", teamIdsUserIsMemberOf)}");

        return teamIdsUserIsMemberOf;
    }

    private string GetUserTournamentsKey(string userId)
    {
        return $"{_redisOptions.Value.KeyPrefix}:{userId}:tournamentIds";
    }

    /// <summary>
    /// Given userId, return the tournament ids the user can manage.
    /// First check redis, if not set, then check database
    /// </summary>
    private async Task<List<string>> GetUserTournamentIds(
        string userId,
        List<string> teamIdsUserIsMemberOf
        )
    {
        // Get tournaments owned by teams the user is a member of
        List<string> tournamentIdsOwnedByTeamsUserIsMemberOf = null;

        // First attempt to get values from Redis
        // If values not set, then get from database and write to Redis
        var db = _connectionMultiplexer.GetDatabase();
        var userTournamentIdsKey = GetUserTournamentsKey(userId);
        var doesKeyExist = await db.KeyExistsAsync(userTournamentIdsKey);
        if (doesKeyExist)
        {
            var tournamentIdsAsRedisValues = await db.SetMembersAsync(userTournamentIdsKey);
            if (tournamentIdsAsRedisValues != null && tournamentIdsAsRedisValues.Length > 0)
            {
                tournamentIdsOwnedByTeamsUserIsMemberOf = tournamentIdsAsRedisValues.Select(val => val.ToString()).ToList();
                _logger.LogInformation($"User tournament ids found in Redis!");
            }
        }

        if (tournamentIdsOwnedByTeamsUserIsMemberOf == null)
        {
            _logger.LogInformation($"User tournament ids NOT found in Redis. Retrieving from database");
            tournamentIdsOwnedByTeamsUserIsMemberOf = (await _tournamentsService.GetTournamentIdsOwnedByTeamIds(teamIdsUserIsMemberOf)).ToList();

            foreach (var tournamentIdUserCanManage in tournamentIdsOwnedByTeamsUserIsMemberOf)
            {
                await db.SetAddAsync(userTournamentIdsKey, tournamentIdUserCanManage);
            }

            await db.KeyExpireAsync(userTournamentIdsKey, TimeSpan.FromMinutes(_redisOptions.Value.KeyExpirationMinutesNumber));
            _logger.LogInformation($"Saved tournament ids to Redis at key {userTournamentIdsKey}");
        }

        _logger.LogDebug($"User is allowed to edit resources of tournaments: {string.Join(", ", tournamentIdsOwnedByTeamsUserIsMemberOf)}");

        return tournamentIdsOwnedByTeamsUserIsMemberOf;
    }

    private bool CanUserBypassAuthorization(ClaimsPrincipal user)
    {
        var apiKeyIdentity = user.Claims.FirstOrDefault(c => c.Type == _apiKeyAuthOptions.Value.IdentityClaim)?.Value;
        var isApiKeyForPlugin = !string.IsNullOrEmpty(apiKeyIdentity) && apiKeyIdentity == _apiKeyAuthOptions.Value.PluginName;

        return isApiKeyForPlugin;
    }
}

public class UserOwnershipData
{
    [Required]
    public string UserId { get; set; } = "NOT_SET";

    [Required]
    public List<string> TeamIds { get; init; } = new List<string>();

    [Required]
    public List<string> TournamentIds { get; init; } = new List<string>();
}