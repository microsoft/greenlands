using Greenlands.Api.Utilities;

namespace Greenlands.Api.Models;

public class TeamInput
{
    [Required]
    public string Name { get; init; }
}

/// <summary>
/// Represents a team of people/organizers on the platform.
/// </summary>
public class Team : TeamInput
{
    /// <summary>
    /// The ID of the team.
    /// </summary>
    [Required]
    public string Id { get; init; } = Guid.NewGuid().ToString();

    [Required]
    public DateTimeOffset Created { get; init; } = DateTimeOffset.Now;

    [Required]
    public DateTimeOffset Updated { get; set; } = DateTimeOffset.Now;

    /// <summary>
    /// The IDs of the members of the team.
    /// </summary>
    // TODO: Want this to be ISet<string> and HashSet<string> but the typing / deserialization does not work on OpenAPI Generator
    // Output would be typed as Set even though it is still the default array
    [Required]
    public IList<string> MemberIds { get; init; } = new List<string>();

    // Empty constructor not used, but seems required for Cosmos to create instances of items
    public Team()
    {
    }

    public Team(TeamInput teamInput)
    {
        MappingUtilities.MapPropertiesFromSourceToDestination(teamInput, this);
    }
}
