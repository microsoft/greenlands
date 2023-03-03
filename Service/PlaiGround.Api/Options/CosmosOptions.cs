namespace PlaiGround.Api.Options;

public class CosmosOptions
{
    public const string Section = "CosmosDb";

    public string DatabaseName { get; set; }

    public string AccountEndpoint  { get; set; }

    public string Key { get; set; }
}
