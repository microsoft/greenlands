using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Greenlands.Api.Utilities;

namespace Greenlands.Api.Models;

[JsonConverter(typeof(StringEnumConverter))]
public enum AgentServiceType
{
    Baseline,
    Competitor,
}

public class AgentServiceInput
{
    /// <summary>
    /// The name of the agent.
    /// </summary>
    [Required]
    public string Name { get; set; }

    /// <summary>
    /// URL where the source code of the agent can be found.
    /// </summary>
    [Required]
    public Uri SourceCodeUrl { get; set; }

    /// <summary>
    /// ID of the Agent Challenge to which this agent was registered. An
    /// AgentService participates in a single agent challenge.
    /// </summary>
    [Required]
    public string AgentChallengeId { get; init; }

    /// <summary>
    /// ID of the team that owns this agent
    /// </summary>
    public string? OwningTeamId { get; init; }

    /// <summary>
    /// The type of agent. `Baseline` is to be used for the "baseline" agent of
    /// the challenge if there is one. The baseline is provided by the
    /// organizers of the challenge. All other agents should be `Competitor`s.
    /// </summary>
    [Required]
    public AgentServiceType Type { get; init; } = AgentServiceType.Competitor;
}

/// <summary>
/// Represents a specific agent.
/// </summary>
public class AgentService : AgentServiceInput
{
    [Required]
    public string Id { get; init; } = Guid.NewGuid().ToString();

    [Required]
    public DateTimeOffset Created { get; init; } = DateTimeOffset.Now;

    [Required]
    public DateTimeOffset Updated { get; set; } = DateTimeOffset.Now;

    [Required]
    public string CreatedByUserId { get; init; }

    // Empty constructor not used, but seems required for Cosmos to create instances of items
    public AgentService()
    {
    }

    public AgentService(string userId, AgentServiceInput agentServiceInput)
    {
        CreatedByUserId = userId;
        MappingUtilities.MapPropertiesFromSourceToDestination(agentServiceInput, this);
    }
}

/// <summary>
/// Information that tells the an agent how it should communicate with the rest
/// of the system.
/// </summary>
public class AgentConnectionInfo
{
    /// <summary>
    /// The URL of the blob storage container where the agent can find the data
    /// for a given task.
    /// </summary>
    [Required]
    public Uri BlobStorageContainerUrl { get; init; }

    /// <summary>
    /// Publish and subscribe SAS token for the agent to use to communicate with
    /// the system through Event Hub.
    /// </summary>
    [Required]
    public string PublishAndSubscribeSharedAccessSignature { get; init; }

    /// <summary>
    /// Publish and subscribe connection string for the agent to use to
    /// communicate with the system through Event Hub.
    /// </summary>
    [Required]
    public string PublishAndSubscribeConnectionString { get; init; }
}