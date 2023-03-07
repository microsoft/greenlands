using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace Greenlands.Api.Events.v1;

[SwaggerSchema("Any player sends a chat message (does not include commands)")]
public class PlayerChatEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(PlayerChatEvent))]
    public override string EventType => nameof(PlayerChatEvent);

    [Required]
    public string Message { get; init; }
}
