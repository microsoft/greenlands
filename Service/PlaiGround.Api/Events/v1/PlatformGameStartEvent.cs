using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace PlaiGround.Api.Events.v1;

[SwaggerSchema("Triggered when a game starts, before any other event")]
public class PlatformGameStartEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(PlatformGameStartEvent))]
    public override string EventType => nameof(PlatformGameStartEvent);
}
