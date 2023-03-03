using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace PlaiGround.Api.Controllers;

/// <summary>
/// Simple endpoint to monitor the health of the Service.
/// </summary>
[AllowAnonymous]
[ApiVersion("1.0")]
[Route("api/v{v:apiVersion}/[controller]")]
[ApiController]
public class HealthController : ControllerBase
{
    /// <summary>
    /// Returns a simple message to indicate that the service is running.
    /// </summary>
    [HttpGet(Name = "GetHealth")]
    public ActionResult Get()
    {
        return Ok($"PlaiGround Service is running.... {DateTime.Now}");
    }
}
