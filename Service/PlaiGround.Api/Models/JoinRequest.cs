using Newtonsoft.Json;
using PlaiGround.Api.Utilities;

namespace PlaiGround.Api.Models;

public class JoinRequestInput
{
    /// <summary>
    /// The unique identifier of the team to which the join request refers to.
    /// </summary>
    [Required]
    public string TeamId { get; init; }

    /// <summary>
    /// The unique identifier of the user who made the join request.
    /// </summary>
    [Required]
    public string UserId { get; init; }
}

/// <summary>
/// Represents a request to join a team.
/// </summary>
public class JoinRequest : JoinRequestInput
{
    /// <summary>
    /// The unique identifier of the join request.
    /// </summary>
    [Required]
    public string Id { get; init; } = Guid.NewGuid().ToString();

    public JoinRequest()
    {
    }

    public JoinRequest(JoinRequestInput joinRequestInput)
    {
        MappingUtilities.MapPropertiesFromSourceToDestination(joinRequestInput, this);
    }
}
