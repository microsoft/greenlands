namespace Greenlands.Api.Options;

public class EventHubOptions
{
    public const string Section = "EventHub";

    public string FullyQualifiedNamespace { get; set; }

    public string EventHubName { get; set; }

    public string ConsumerGroup { get; set; }

    // This policy is used for services evaluation abilities
    // Because evaluation by service is less practical and rarely used consider removing the feature
    public string NamespaceSendListenSharedAccessKeyName { get; set; }

    public string NamespaceSendListenSharedAccessKey { get; set; }

    // This policy is used to generate SEND only SAS for Agents
    public string EventHubSendListenSharedAccessKeyName { get; set; }

    public string EventHubSendListenSharedAccessKey { get; set; }
}
