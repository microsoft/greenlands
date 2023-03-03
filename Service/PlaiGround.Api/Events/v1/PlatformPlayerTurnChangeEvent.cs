using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace PlaiGround.Api.Events.v1;

[JsonConverter(typeof(StringEnumConverter))]
public enum TurnChangeReason
{
    ABORT_TIME_OUT,
    PLATFORM_GAME_START,
    PLAYER_COMMAND,
}

[SwaggerSchema("Indicates the active turn changed from one role to the next.")]
public class PlatformPlayerTurnChangeEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(PlatformPlayerTurnChangeEvent))]
    public override string EventType => nameof(PlatformPlayerTurnChangeEvent);

    [Required]
    public TurnChangeReason Reason { get; init; } = TurnChangeReason.PLAYER_COMMAND;

    // This may be null when sent as an action from the agent.
    public string? NextActiveRoleId { get; init; }

    public string? PreviousActiveRoleId { get; init; }
}
