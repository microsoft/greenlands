namespace PlaiGround.Api.Options;

public class RedisOptions
{
    public const string Section = "Redis";

    public string Endpoint { get; init; }

    public string KeyPrefix { get; init; }

    public string KeyExpirationMinutes { get; init; }

    public int KeyExpirationMinutesNumber {
        get
        {
            return int.Parse(KeyExpirationMinutes);
        }
    }
}
