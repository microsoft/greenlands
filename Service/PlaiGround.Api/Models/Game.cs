using Newtonsoft.Json;
using Newtonsoft.Json.Converters;

namespace PlaiGround.Api.Models;

[JsonConverter(typeof(StringEnumConverter))]
public enum GameCompletionType
{
    /// <summary>
    /// The player has said that the game has ended but it did not end
    /// successfully. This can happen with the agent fails to properly complete
    /// the player's instructions.
    /// </summary>
    PLAYER_COMMAND_FAIL,

    /// <summary>
    /// The player has said that the game has ended and it ended successfully
    /// (the agent was able to complete everything).
    /// </summary>
    PLAYER_COMMAND_SUCCESS,

    /// <summary>
    /// The game ended because the running time of the game exceeded the time
    /// limit specified for the task. <see cref="GameLimits" />.
    /// </summary>
    ABORT_TIME_OUT,

    /// <summary>
    /// The game ended because the amount of turns in the game has exceeded the
    /// max-turns limit specified for the task. <see cref="GameLimits" />.
    /// </summary>
    ABORT_MAX_TURN,

    /// <summary>
    /// The game ended because (one of) the player has left the game.
    /// </summary>
    ABORT_PLAYER_LEAVE
}

public class GameUpdate
{
    public GameCompletionType? CompletionType { get; init; }
}

/// <summary>
/// Represents a specific game, whether it is in progress or completed.
/// </summary>
public class Game
{
    /// <summary>
    /// The ID of the game.
    /// </summary>
    [Required]
    public string Id { get; init; } = Guid.NewGuid().ToString();

    /// <summary>
    /// The ID of the task for which the game was created.
    /// </summary>
    [Required]
    public string TaskId { get; init; }

    /// <summary>
    /// After a game ended and its data is saved this will be set to the blob uri.
    /// </summary>
    [Required]
    public Uri GameDataBlobUri { get; set; }

    [Required]
    public DateTimeOffset Created { get; init; } = DateTimeOffset.UtcNow;

    /// <summary>
    /// If the game has finished then this specifies why it finished.
    /// </summary>
    public GameCompletionType? CompletionType { get; set; }

    // Empty constructor not used, but seems required for Cosmos to create instances of items
    public Game()
    {
    }

    public Game(string taskId)
    {
        TaskId = taskId;
    }
}
