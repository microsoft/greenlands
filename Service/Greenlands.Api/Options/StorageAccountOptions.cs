namespace Greenlands.Api.Options;

public class StorageAccountOptions
{
    public const string Section = "StorageAccount";

    public string ConnectionString { get; init; } = "NOT_SET";

    public string HumanChallengeDataContainerName { get; init; } = "NOT_SET";

    public string AgentChallengeDataContainerName { get; init; } = "NOT_SET";

    public string TaskDataContainerName { get; init; } = "NOT_SET";

    public string GameDataContainerName { get; init; } = "NOT_SET";
}
