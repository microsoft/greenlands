using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace PlaiGround.Api.Events.v1;

[SwaggerSchema("Indicates that a game has ended. Consumers may assume this is the last event of a game.")]
public class PlatformGameEndEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(PlatformGameEndEvent))]
    public override string EventType => nameof(PlatformGameEndEvent);
}
