namespace Greenlands.Api.Models;

/// <summary>
/// Represents a block in Minecraft.
/// </summary>
public record Block
{
    /// <summary>
    /// Blocks in Minecraft are represented by numerical IDs, and you can find
    /// the list of all IDs we can use here: See:
    /// https://github.com/Bukkit/Bukkit/blob/master/src/main/java/org/bukkit/Material.java#L64-L416
    /// </summary>
    [Required]
    public int Type { get; init; }

    public Block()
    {
    }

    public Block(int materialType)
    {
        Type = materialType;
    }

    public override string ToString()
    {
        return $"Block({Type})";
    }
}
