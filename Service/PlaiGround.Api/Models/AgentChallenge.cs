using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using PlaiGround.Api.Utilities;
using Swashbuckle.AspNetCore.Annotations;

namespace PlaiGround.Api.Models;

public class AgentChallengeInput : ChallengeBaseInput
{
    [Required]
    public string TournamentRoleIdSupported { get; init; }

    [Required]
    [MinLength(1, ErrorMessage ="You have not selected any human challenges. Please select at least one.")]
    public IList<string> HumanChallengeIds { get; init; } = new List<string>();

    [SwaggerSchema("Link to scripts which transform Human Challenge data into the data set used for Training, Validation, and Testing.")]
    [Required]
    public Uri DataPreparationScriptsUrl { get; init; }

    [SwaggerSchema("Link to data set that would be created by running the scripts.")]
    [Required]
    public Uri DataSetUrl { get; init; }
}

public class AgentChallenge : AgentChallengeInput
{
    [Required]
    public string Id { get; init; } = Guid.NewGuid().ToString();

    [Required]
    public DateTimeOffset Created { get; init; } = DateTimeOffset.Now;

    [Required]
    public AgentChallengeState State { get; set; } = AgentChallengeState.Active;

    [SwaggerSchema("The date time when the challenge was ended.")]
    public DateTimeOffset? Ended { get; set; }

    [SwaggerSchema("The link to JSON data in blob storage.")]
    public Uri? DownloadUrl { get; set; }

    [Required]
    public AgentChallengeDataCollectionInfo DataCollectionInfo { get; set; } = new AgentChallengeDataCollectionInfo();

    // Empty constructor not used, but seems required for Cosmos to create instances of items
    public AgentChallenge()
    {
    }

    public AgentChallenge(AgentChallengeInput agentChallengeInput)
    {
        MappingUtilities.MapPropertiesFromSourceToDestination(agentChallengeInput, this);
    }
}

[JsonConverter(typeof(StringEnumConverter))]
public enum AgentChallengeState
{
    Active,
    Ended
}