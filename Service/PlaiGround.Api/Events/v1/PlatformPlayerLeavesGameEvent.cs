using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace PlaiGround.Api.Events.v1;

[SwaggerSchema("Player toggled flight on/off")]
public class PlatformPlayerLeavesGameEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(PlatformPlayerLeavesGameEvent))]
    public override string EventType => nameof(PlatformPlayerLeavesGameEvent);
}
