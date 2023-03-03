using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using PlaiGround.Api.Utilities;
using Swashbuckle.AspNetCore.Annotations;

namespace PlaiGround.Api.Models;

public class HumanChallengeQueryOptions
{
    public HashSet<string>? HumanChallengeIds { get; set; }
}

public class HumanChallengeInput : ChallengeBaseInput
{
    [Required]
    [MinLength(1, ErrorMessage ="You have not selected any tasks. Please select at least one.")]
    public IList<string> TaskIds { get; init; }
}

/// <summary>
/// Represents a human human challenge. The idea of human-human challenges is to
/// collect human interaction data for a specific set of tasks. The idea is that
/// this data can then be used to train agents to perform the same tasks.
///
/// NOTE: The system does not really use human challenges.
/// </summary>
public class HumanChallenge : HumanChallengeInput
{
    /// <summary>
    /// The unique identifier for the human challenge.
    /// </summary>
    [Required]
    public string Id { get; init; } = Guid.NewGuid().ToString();

    [Required]
    public DateTimeOffset Created { get; init; } = DateTimeOffset.Now;

    [Required]
    public HumanChallengeState State { get; set; } = HumanChallengeState.Active;

    [SwaggerSchema("The date time when the challenge was ended.")]
    public DateTimeOffset? Ended { get; set; }

    [SwaggerSchema("The link to JSON data in blob storage.")]
    public Uri? DownloadUrl { get; set; }

    [Required]
    public ChallengeDataCollectionInfo DataCollectionInfo { get; set; } = new ChallengeDataCollectionInfo();

    // Empty constructor not used, but seems required for Cosmos to create instances of items
    public HumanChallenge()
    {
    }

    public HumanChallenge(HumanChallengeInput humanChallengeInput)
    {
        MappingUtilities.MapPropertiesFromSourceToDestination(humanChallengeInput, this);
    }
}

[JsonConverter(typeof(StringEnumConverter))]
public enum HumanChallengeState
{
    Active,
    Ended
}
