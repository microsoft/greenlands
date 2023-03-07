using Greenlands.Api.Events.v1.Helpers;
using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace Greenlands.Api.Events.v1;

[SwaggerSchema("A block is placed by a player")]
public class BlockPlaceEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(BlockPlaceEvent))]
    public override string EventType => nameof(BlockPlaceEvent);

    [Required]
    public Location Location { get; init; }

    [Required]
    [SwaggerSchema("Material that was placed")]
    public int Material { get; init; }
}
