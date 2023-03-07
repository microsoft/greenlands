using Greenlands.Api.Events.v1.Helpers;
using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace Greenlands.Api.Events.v1;

[SwaggerSchema("Any player moves from one location to another")]
public class PlayerMoveEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(PlayerMoveEvent))]
    public override string EventType => nameof(PlayerMoveEvent);

    [Required]
    public Location NewLocation { get; init; }

}

