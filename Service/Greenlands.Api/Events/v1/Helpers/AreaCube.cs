namespace Greenlands.Api.Events.v1.Helpers;

public record AreaCube
{
    [Required]
    public Location origin { get; set; }

    [Required]
    public Location size { get; set; }


    public AreaCube()
    {
    }

    public AreaCube(Location origin, Location size)
    {
        this.origin = origin;
        this.size = size;
    }
}
