using Greenlands.Api.Services;

namespace Greenlands.Api.Models;

public class ClientErrorResponse
{
    public Dictionary<string, List<string>>? Errors { get; set; }
}
