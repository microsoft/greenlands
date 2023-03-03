using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace PlaiGround.Api.Events.v1;

[SwaggerSchema("Player toggled flight on/off")]
public class PlayerToggleFlightEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(PlayerToggleFlightEvent))]
    public override string EventType => nameof(PlayerToggleFlightEvent);

    [Required]
    [SwaggerSchema("Whether the player toggled flying ON or OFF")]
    public bool IsFlying { get; init; }
}
