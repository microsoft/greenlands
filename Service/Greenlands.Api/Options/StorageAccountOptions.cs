namespace Greenlands.Api.Options;

public class StorageAccountOptions
{
    public const string Section = "StorageAccount";

    public string ConnectionString { get; set; }

    public string HumanChallengeDataContainerName { get; set; }

    public string AgentChallengeDataContainerName { get; set; }

    public string TaskDataContainerName { get; set; }

    public string GameDataContainerName { get; set; }
}
