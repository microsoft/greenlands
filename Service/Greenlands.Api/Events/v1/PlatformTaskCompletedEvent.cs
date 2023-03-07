using Swashbuckle.AspNetCore.Annotations;
using System.ComponentModel;

namespace Greenlands.Api.Events.v1;

[SwaggerSchema("Indicates that the task has been completed.")]
public class PlatformTaskCompletedEvent : BaseEvent
{
    [Required]
    [DefaultValue(nameof(PlatformTaskCompletedEvent))]
    public override string EventType => nameof(PlatformTaskCompletedEvent);

    [SwaggerSchema("This property tells us in which way did the game complete.")]
    public GameCompletionType? CompletionType { get; init; }
}
