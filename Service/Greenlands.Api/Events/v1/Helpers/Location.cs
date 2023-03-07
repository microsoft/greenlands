namespace Greenlands.Api.Events.v1.Helpers;

public record Location
{
    [Required]
    public float X { get; init; }

    [Required]
    public float Y { get; init; }

    [Required]
    public float Z { get; init; }

    [Required]
    public float Pitch { get; init; }

    [Required]
    public float Yaw { get; init; }

    public Location()
    {
    }

    // When location is for a block, the pitch and yaw should always be 0
    public Location(float x = 0, float y = 0, float z = 0, float pitch = 0, float yaw = 0)
    {
        X = x;
        Y = y;
        Z = z;
        Pitch = pitch;
        Yaw = yaw;
    }

    public static Location FromString(string s)
    {
        var sNoBrackets = s.Substring(1, s.Length - 2);
        var floatValues = sNoBrackets.Split(',').Select(s => float.Parse(s));

        return new Location(
            floatValues.ElementAt(0),
            floatValues.ElementAt(1),
            floatValues.ElementAt(2),
            floatValues.ElementAt(3),
            floatValues.ElementAt(4)
        );
    }

    public override string ToString()
    {
        return $"[{X},{Y},{Z},{Pitch},{Yaw}]";
    }
}
