using Greenlands.Api.Events.v1.Helpers;
using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace Greenlands.Api.Events.v1;

[SwaggerSchema("Player toggled flight on/off")]
public class PlatformPlayerJoinsGameEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(PlatformPlayerJoinsGameEvent))]
    public override string EventType => nameof(PlatformPlayerJoinsGameEvent);

    [Required]
    [SwaggerSchema("Id of the player that caused this event")]
    public string PlayerId { get; init; }

    [Required]
    [SwaggerSchema("The location at which the player spawned")]
    public Location SpawnLocation { get; init; }
}
