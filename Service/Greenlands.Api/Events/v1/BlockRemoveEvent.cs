using Greenlands.Api.Events.v1.Helpers;
using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace Greenlands.Api.Events.v1;

[SwaggerSchema("A block is broken/removed by a player")]
public class BlockRemoveEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(BlockRemoveEvent))]
    public override string EventType => nameof(BlockRemoveEvent);

    [Required]
    public Location Location { get; init; }
}
