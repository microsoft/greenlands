using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Greenlands.Api.Utilities;

namespace Greenlands.Api.Models;

[JsonConverter(typeof(StringEnumConverter))]
public enum GameMode
{
    Survival,
    Creative,
    Spectator,
    Adventure
}

public class TournamentRoleCapabilities
{
    /// <summary>
    /// Whether the player with this role can be seen by other players in the
    /// game or not.
    /// </summary>
    [Required]
    public bool CanBeSeenByOtherPlayers { get; init; } = false;

    /// <summary>
    /// In an architect-builder game, if this is True then the player with this
    /// role will be able to see the target game state.
    /// </summary>
    [Required]
    public bool CanSeeTargetGameState { get; init; } = false;
}

public class TournamentRoleActions
{
    [Required]
    public bool CanPlaceBlocks { get; init; } = false;

    [Required]
    public bool CanRemoveBlocks { get; init; } = false;

    [Required]
    public bool CanSendTextMessage { get; init; } = false;

    /// <summary>
    /// Whether the player can evaluate a game. If true then the player with
    /// this role can end the game as well as tell if the game ended
    /// successfully or not.
    /// </summary>
    [Required]
    public bool CanEvaluate { get; init; } = false;

    [Required]
    public bool CanToggleFlight { get; init; } = false;
}

/// <summary>
/// The properties of a tournament role which may be updated
/// </summary>
public class TournamentRoleUpdate
{
    [Required]
    public string Id { get; init; }

    public string Name { get; init; }

    public string Description { get; init; }
}

public class TournamentRoleInput
{
    /// <summary>
    /// The name of the tournament role.
    /// </summary>
    [Required]
    public string Name { get; set; }

    /// <summary>
    /// The description of the tournament role.
    /// </summary>
    public string Description { get; set; } = string.Empty;

    /// <summary>
    /// Description of the capabilities that this role has.
    /// </summary>
    [Required]
    public TournamentRoleCapabilities Capabilities { get; init; }

    /// <summary>
    /// Description of the actions that this role can perform.
    /// </summary>
    [Required]
    public TournamentRoleActions Actions { get; init; }

    /// <summary>
    /// Whether players can play this role more than once.
    /// </summary>
    [Required]
    public bool CanRoleBePlayedMultipleTimes { get; init; } = false;

    /// <summary>
    /// The Minecraft game mode that this role will play in.
    /// </summary>
    [Required]
    public GameMode GameMode { get; init; }
}

public class TournamentRole : TournamentRoleInput
{
    /// <summary>
    /// The unique identifier for the tournament role.
    /// </summary>
    [Required]
    public string Id { get; init; } = Guid.NewGuid().ToString();

    // Empty constructor not used, but seems required for Cosmos to create instances of items
    public TournamentRole()
    {
    }

    public TournamentRole(TournamentRoleInput tournamentRoleInput)
    {
        MappingUtilities.MapPropertiesFromSourceToDestination(tournamentRoleInput, this);
    }
}

/// <summary>
/// The properties of a tournament which may be udpated
/// </summary>
public class TournamentUpdate
{
    public string? Name { get; init; }

    public TournamentState? State { get; init; }

    public string? Instructions { get; init; }

    /// <summary>
    /// List of updates to perform to the tournament's roles
    /// </summary>
    public IList<TournamentRoleUpdate>? Roles { get; init; }
}

public class TournamentInput
{
    /// <summary>
    /// The unique identifier for the team that the tournament belongs to.
    /// </summary>
    [Required]
    public string TeamId { get; init; }

    /// <summary>
    /// The name of the tournament.
    /// </summary>
    [Required]
    public string Name { get; set; }

    /// <summary>
    /// The state the tournament is in.
    /// </summary>
    [Required]
    public TournamentState State { get; set; } = TournamentState.InviteOnly;

    /// <summary>
    /// Whether to use the MatchedWorldBlocksEvaluator or not. Note: this is not
    /// used. We always rely on human input to evaluate whether tasks have been
    /// successful or not.
    /// </summary>
    [Required]
    public bool UseMatchedWorldBlocksEvaluator { get; init; } = false;

    /// <summary>
    /// Whether to use the MatchedPlayerInventoryEvaluator or not. Note: this is
    /// not used. We always rely on human input to evaluate whether tasks have
    /// been successful or not.
    /// </summary>
    [Required]
    public bool UseMatchedPlayerInventoryEvaluator { get; init; } = false;

    /// <summary>
    /// Whether to use the MatchedPlayerHoldEvaluator or not. Note: this is not
    /// used. We always rely on human input to evaluate whether tasks have been
    /// successful or not.
    /// </summary>
    [Required]
    public bool UseMatchedPlayerHoldEvaluator { get; init; } = false;

    /// <summary>
    /// Whether to use the MatchedPlayerLocationEvaluator or not. Note: this is not
    /// used. We always rely on human input to evaluate whether tasks have been
    /// successful or not.
    /// </summary>
    [Required]
    public bool UseMatchedPlayerLocationEvaluator { get; init; } = false;

    /// <summary>
    /// Whether to use the AnsweredQuestionEvaluator or not. Note: this is not
    /// used. We always rely on human input to evaluate whether tasks have been
    /// successful or not.
    /// </summary>
    [Required]
    public bool UseAnsweredQuestionEvaluator { get; init; } = false;

    /// <summary>
    /// Whether to use the PlayerConfirmationEvaluator or not. Note: this is not
    /// used. We always rely on human input to evaluate whether tasks have been
    /// successful or not.
    /// </summary>
    [Required]
    public bool UsePlayerConfirmationEvaluator { get; init; } = false;

    /// <summary>
    /// Definition of the roles that are to be part of the tournament.
    /// </summary>
    [Required]
    [MinLength(1)]
    public IList<TournamentRoleInput> Roles { get; init; } = new List<TournamentRoleInput>();

    /// <summary>
    /// The instructions for the tournament, will be displayed to human players
    /// when a game for a task in this tournament starts.
    /// </summary>
    public string Instructions { get; set; } = string.Empty;

    public Dictionary<string, bool> GetEvauatorsDictionary()
    {
        return new Dictionary<string, bool>
        {
            ["UseMatchedWorldBlocksEvaluator"] = this.UseMatchedWorldBlocksEvaluator,
            ["UseMatchedPlayerInventoryEvaluator"] = this.UseMatchedPlayerInventoryEvaluator,
            ["UseMatchedPlayerHoldEvaluator"] = this.UseMatchedPlayerHoldEvaluator,
            ["UseMatchedPlayerLocationEvaluator"] = this.UseMatchedPlayerLocationEvaluator,
            ["UseAnsweredQuestionEvaluator"] = this.UseAnsweredQuestionEvaluator,
            ["UsePlayerConfirmationEvaluator"] = this.UsePlayerConfirmationEvaluator,
        };
    }
}

public class Tournament : TournamentInput
{
    /// <summary>
    /// The unique identifier for the tournament
    /// </summary>
    [Required]
    public string Id { get; init; } = Guid.NewGuid().ToString();

    [Required]
    public DateTimeOffset Created { get; init; } = DateTimeOffset.UtcNow;

    [Required]
    public DateTimeOffset Updated { get; set; } = DateTimeOffset.UtcNow;

    /// <summary>
    /// The definition of each of the roles that are part of the tournament
    /// </summary>
    [Required]
    [MinLength(1)]
    public new IList<TournamentRole> Roles { get; init; } = new List<TournamentRole>();

    // Empty constructor not used, but seems required for Cosmos to create instances of items
    public Tournament()
    {
    }

    public Tournament(TournamentInput tournamentInput)
    {
        // Can't use MappingUtilities.MapPropertiesFromSourceToDestination here since there is custom transformation of one properties.
        // This would require https://github.com/AutoMapper/AutoMapper
        TeamId = tournamentInput.TeamId;
        Name = tournamentInput.Name;
        State = tournamentInput.State;
        Instructions = tournamentInput.Instructions;
        Roles = tournamentInput.Roles.Select(tri => new TournamentRole(tri)).ToList();
       
        UseMatchedWorldBlocksEvaluator = tournamentInput.UseMatchedWorldBlocksEvaluator;
        UseMatchedPlayerInventoryEvaluator = tournamentInput.UseMatchedPlayerInventoryEvaluator;
        UseMatchedPlayerHoldEvaluator = tournamentInput.UseMatchedPlayerHoldEvaluator;
        UseMatchedPlayerLocationEvaluator = tournamentInput.UseMatchedPlayerLocationEvaluator;
        UseAnsweredQuestionEvaluator = tournamentInput.UseAnsweredQuestionEvaluator;
        UsePlayerConfirmationEvaluator = tournamentInput.UsePlayerConfirmationEvaluator;
    }

    /// <summary>
    /// Given a tournament update object apply the updated values to the existing tournament.
    /// </summary>
    public void ApplyUpdate(TournamentUpdate tournamentUpdate)
    {
        this.Updated = DateTimeOffset.UtcNow;

        if (tournamentUpdate == null)
        {
            return;
        }

        if (!string.IsNullOrWhiteSpace(tournamentUpdate.Name))
        {
            Name = tournamentUpdate.Name;
        }

        if (tournamentUpdate.State != null)
        {
            State = (TournamentState)tournamentUpdate.State;
        }

        if (!string.IsNullOrWhiteSpace(tournamentUpdate.Instructions))
        {
            Instructions = tournamentUpdate.Instructions;
        }

        var roles = tournamentUpdate?.Roles ?? new List<TournamentRoleUpdate>();
        foreach (var tournamentRoleUpdate in roles)
        {
            var existingRole = Roles.FirstOrDefault(tr => tr.Id == tournamentRoleUpdate.Id);
            if (existingRole != null)
            {
                if (!string.IsNullOrWhiteSpace(tournamentRoleUpdate.Name))
                {
                    existingRole.Name = tournamentRoleUpdate.Name;
                }

                if (!string.IsNullOrWhiteSpace(tournamentRoleUpdate.Description))
                {
                    existingRole.Description = tournamentRoleUpdate.Description;
                }
            }
        }
    }
}

[JsonConverter(typeof(StringEnumConverter))]
public enum TournamentState
{
    /// <summary>
    /// Only specific users may choose or be given a task in this Tournament
    /// </summary>
    InviteOnly,

    /// <summary>
    /// Any player can participate in this Tournament
    /// </summary>
    Public,
    
    /// <summary>
    // Tournament data is preserved but games will not be played using its tasks
    /// </summary>
    Archived
}
