// Definte global types alias to give semantic name to dictionary type.
global using RoleId = System.String;
global using LocationString = System.String;
global using ItemType = System.Int32;
global using ItemQuantity = System.Int32;

using Greenlands.Api.Events.v1.Helpers;

namespace Greenlands.Api.Models;

/// <summary>
/// Represents changes that are to happen in a game.
/// </summary>
public class GameChanges
{
    /// <summary>
    /// Represents the changes that should happen in a world in a game.
    /// </summary>
    public WorldChanges? WorldChanges { get; set; }

    /// <summary>
    /// Represents the changes that a player with the specified role should
    /// undergo.
    /// </summary>
    public Dictionary<RoleId, PlayerChanges>? PlayerChanges { get; set; }
}

/// <summary>
/// Represents state of the world.
/// </summary>
public class WorldChanges
{
    /// <summary>
    /// Represents the state of the world's blocks.
    /// </summary>
    public Dictionary<LocationString, Block>? BlockChanges { get; set; }
}

/// <summary>
/// Represents state of a player.
/// </summary>
public class PlayerChanges
{
    /// <summary>
    /// Represents the state of a player's inventory.
    /// </summary>
    // TODO: Do we need to represent movement within inventory locations?
    public Dictionary<ItemType, ItemQuantity>? InventoryChanges { get; set; }

    /// <summary>
    /// Represents the state of a player's location.
    /// </summary>
    public Location? Location { get; set; }

    /// <summary>
    /// Represents the state of a player's current item if any.
    /// </summary>
    public Block? CurrentItem { get; set; }
}
