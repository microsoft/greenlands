using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace Greenlands.Api.Events.v1;

[SwaggerSchema("Agent is ready to participate in games")]
public class AgentIsReadyEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(AgentIsReadyEvent))]
    public override string EventType => nameof(AgentIsReadyEvent);

    [SwaggerSchema("Id of the AgentService that represents this agent")]
    public string AgentServiceId { get; init; }

    [SwaggerSchema("The maximum number of games that this agent is expected to play at once")]
    public int MaxGames { get; init; }
}
