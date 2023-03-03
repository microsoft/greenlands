using PlaiGround.Api.Services;

namespace PlaiGround.Api.Models;

public class ClientErrorResponse
{
    public Dictionary<string, List<string>>? Errors { get; set; }
}
