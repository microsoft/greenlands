using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Greenlands.Api.Utilities;

namespace Greenlands.Api.Models;

[JsonConverter(typeof(StringEnumConverter))]
public enum TaskState
{
    Draft,
    Published
}

/// <summary>
/// Limits that apply throughout a specific game.
/// </summary>
public class GameLimits
{
    /// <summary>
    /// The maximum amount of seconds that a game is allowed to run for.
    /// </summary>
    public int? MaxTimeOutSeconds { get; init; }

    /// <summary>
    /// The maximum amount of turns that a game is allowed to run for.
    /// </summary>
    public int? MaxTurnLimit { get; init; }
}

/// <summary>
/// Limits that apply during a single turn of a player.
/// </summary>
public class TurnLimits
{
    /// <summary>
    /// The maximum amount of time that a player is allowed to take to complete
    /// a turn.
    /// </summary>
    public int? MaxTimeOutSeconds { get; init; }
}

public class TaskInput
{
    /// <summary>
    /// This is the maximum expected amount of seconds that an average player
    /// will take to complete the task.
    /// </summary>
    public static int ExpectedCompletionDurationMaximumSeconds = 86400;
    public static readonly string ExpectedCompletionDurationErrorName = "ExpectedCompletionDurationExceeded";

    public static string GetExpectedCompletionDurationErrorText(int expectedCompletionDuration)
    {
        return $"You attempted to create a task with the expected completion duration of {(expectedCompletionDuration / 60)}. The maximum allowed completion duration is {(ExpectedCompletionDurationMaximumSeconds / 60)}. Please try again with a lower duration value.";
    }

    public static bool IsTaskExpectedCompletionDurationOverMaximum(int expectedCompletionDurationSeconds)
    {
        return expectedCompletionDurationSeconds > (ExpectedCompletionDurationMaximumSeconds);
    }

    /// <summary>
    /// The name of the task.
    /// </summary>
    [Required]
    public string Name { get; init; } = string.Empty;

    /// <summary>
    /// The state of a task. See <see cref="TaskState"/> for more information.
    /// </summary>
    [Required]
    public TaskState State { get; init; } = TaskState.Draft;

    // TODO: Intended to use TimeSpan but couldn't figure out serialization
    /// <summary>
    /// This is an estimate if how long it would be required for average gamer to complete the task.
    /// It acts a proxy for complexity of target game changes. The more changes required and 
    /// more difficult it is to describe the changes the longer it will take to complete.
    /// </summary>
    [Required]
    public int ExpectedCompletionDurationSeconds { get; init; }

    /// <summary>
    /// The width of the world in blocks. Author may control the X (width) and Z
    /// (depth) size of the world. Y (height) uses Minecraft default (384).
    /// </summary>
    [Required]
    public int WorldSizeX { get; init; }

    /// <summary>
    /// The depth of the world in blocks. Author may control the X (width) and Z
    /// (depth) size of the world. Y (height) uses Minecraft default (384).
    /// </summary>
    [Required]
    public int WorldSizeZ { get; init; }

    /// <summary>
    /// Limits that apply to games created for this task.
    /// </summary>
    public GameLimits? GameLimits { get; init; }

    /// <summary>
    /// The limits for a player with the specified RoleId can take.
    /// </summary>
    public Dictionary<RoleId, TurnLimits>? TurnLimits { get; init; }
}

public class TaskQueryOptions
{
    public string? AgentChallengeId { get; set; }

    public HashSet<string>? TaskIds { get; set; }

    public bool? LoadBlobData { get; init; }
}

/// <summary>
/// Represents a task. A task is a description of a specific
/// configuration/problem that the players need to solve.
/// </summary>
public class GreenlandsTask : TaskInput
{
    /// <summary>
    /// The unique identifier for the task.
    /// </summary>
    [Required]
    public string Id { get; init; } = Guid.NewGuid().ToString();

    [Required]
    public DateTimeOffset Created { get; init; } = DateTimeOffset.UtcNow;

    [Required]
    public DateTimeOffset Updated { get; set; } = DateTimeOffset.UtcNow;

    /// <summary>
    /// The unique identifier for the tournament that the task belongs to.
    /// </summary>
    [Required]
    public string TournamentId { get; init; }

    /// <summary>
    /// URI from which a json containing the initial game state of the task can
    /// be downloaded. See <see cref="GameState"/> for more information.
    /// </summary>
    [Required]
    public Uri InitialGameStateBlobUri { get; init; }

    /// <summary>
    /// URI from which the complete initial world state can be downloaded.
    ///
    /// Complete Blocks = generated world + initial block. Complete block
    /// information is required by the the Agents to represent GameState.
    /// Storing this information keeps the knowledge of how to generate worlds
    /// from strings known only by the Minecraft Plugin
    /// </summary>
    [Required]
    public Uri InitialWorldCompleteBlocksBlobUri { get; init; }

    /// <summary>
    /// URI from which a json containing the target game changes of the task can
    /// be downloaded. See <see cref="GameChanges"/> for more information.
    /// </summary>
    [Required]
    public Uri TargetGameChangesBlobUri { get; init; }

    // Empty constructor not used, but seems required for Cosmos to create instances of items
    public GreenlandsTask()
    {
    }

    public GreenlandsTask(string tournamentId, TaskInput taskInput)
    {
        TournamentId = tournamentId;
        MappingUtilities.MapPropertiesFromSourceToDestination(taskInput, this);
    }
}

/// <summary>
/// Same as <see cref="GreenlandsTask"/> but also includes the initial game
/// state, and target game changes.
/// </summary>
public class CompleteTask: GreenlandsTask
{
    public GameState InitialGameState { get; set; }

    public IList<GameChanges> TargetGameChanges { get; set; }

    public CompleteTask(GreenlandsTask task)
    {
        MappingUtilities.MapPropertiesFromSourceToDestination(task, this);
    }
}