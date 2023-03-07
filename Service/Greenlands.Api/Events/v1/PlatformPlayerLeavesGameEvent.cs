using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace Greenlands.Api.Events.v1;

[SwaggerSchema("Player toggled flight on/off")]
public class PlatformPlayerLeavesGameEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(PlatformPlayerLeavesGameEvent))]
    public override string EventType => nameof(PlatformPlayerLeavesGameEvent);
}
