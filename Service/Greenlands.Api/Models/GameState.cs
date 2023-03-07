using Greenlands.Api.Events.v1.Helpers;
using Swashbuckle.AspNetCore.Annotations;

namespace Greenlands.Api.Models;

/// <summary>
/// A representation of all things the plugin could observe about game state
/// </summary>
public class GameState
{
    /// <summary>
    /// Represents the state of the world.
    /// </summary>
    [Required]
    public WorldState WorldState { get; set; }

    /// <summary>
    /// Represents the state of the players for each role ID.
    /// </summary>
    public Dictionary<RoleId, PlayerState>? PlayerStates { get; set; }

    /// <summary>
    /// Represents the instructions of a game.
    /// </summary>
    [Required]
    public string Instructions { get; init; } = string.Empty;
}

/// <summary>
/// Represents state of the world.
/// </summary>
public class WorldState
{
    /// <summary>
    /// String that specifies generator and arguments to generate base world
    ///
    /// - Format is: {generator name}|arg1:val1|arg2:val2
    /// </summary>
    [Required]
    public string GeneratorName { get; set; }

    /// <summary>
    /// A set of changes to apply to the generated world. Initial World =
    /// Generated World + Changes
    /// </summary>
    public Dictionary<LocationString, Block>? BlockChanges { get; set; }
}

/// <summary>
/// Represents state of a player.
/// </summary>
public class PlayerState
{
    /// <summary>
    /// Dictionary with keys of material ids and values of material quantities.
    /// </summary>
    public Dictionary<ItemType, ItemQuantity>? Inventory { get; set; }

    /// <summary>
    /// The spawn location of the player.
    /// </summary>
    public Location? SpawnLocation { get; set; }

    /// <summary>
    /// The current item of the player.
    /// </summary>
    public Block? CurrentItem { get; set; }

    [SwaggerSchema("If specified, then this cube represents an area in which the player is allowed to move.")]
    public AreaCube? MovementRegion { get; set; }
}
