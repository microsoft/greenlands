using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Swashbuckle.AspNetCore.Annotations;

namespace Greenlands.Api.Events.v1;

[JsonConverter(typeof(StringEnumConverter))]
public enum EventSource
{
    MinecraftPlugin,
    AgentService
}

[SwaggerDiscriminator("eventType")]
public class BaseEvent
{
    [Required]
    [SwaggerSchema(
        "The name of the event type we're sending. This is used as the discriminator to disambiguate among event payloads",
        ReadOnly = true
    )]
    public virtual string EventType { get; init; }

    [Required]
    [SwaggerSchema("The id which identifies this event")]
    public string Id { get; init; }

    [Required]
    [SwaggerSchema("Game id associated with this event")]
    public string GameId { get; init; }

    [Required]
    [SwaggerSchema("Task id associated with this event")]
    public string TaskId { get; init; }

    [Required]
    [SwaggerSchema("Tournament id associated with this event")]
    public string TournamentId { get; init; }

    [Required]
    [SwaggerSchema("Time the event was produced")]
    public string ProducedAtDatetime { get; init; }

    [Required]
    [SwaggerSchema("Tells us which producer this event came from")]
    public EventSource Source { get; init; }

    [SwaggerSchema("If this event was caused by a player then this includes the id of their role")]
    public string? RoleId { get; init; }

    [SwaggerSchema("Identifier to associate a set of events into a group")]
    public string? GroupId { get; init; }

    [SwaggerSchema("If an agent is participating in the game, set property to the ID of the Agent. AgentId MUST be set one all outgoing events for the agent to receive them.")]
    public string? AgentSubscriptionFilterValue { get; init; }
}
