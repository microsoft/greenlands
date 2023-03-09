namespace Greenlands.Api.Options;

public class RedisOptions
{
    public const string Section = "Redis";

    public string Endpoint { get; init; } = "NOT_SET";

    public string KeyPrefix { get; init; } = "NOT_SET";

    public string KeyExpirationMinutes { get; init; } = "NOT_SET";

    public int KeyExpirationMinutesNumber
    {
        get
        {
            return int.Parse(KeyExpirationMinutes);
        }
    }
}
