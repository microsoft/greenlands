using Microsoft.ApplicationInsights.Channel;
using Microsoft.ApplicationInsights.Extensibility;

namespace Greenlands.Api.Telemetry;

public class CloudNameTelemetry : ITelemetryInitializer
{
    public string CloudRoleName { get; set; }

    public string CloudRoleInstance { get; set; }

    public void Initialize(ITelemetry telemetry)
    {
        telemetry.Context.Cloud.RoleName = CloudRoleName;
        telemetry.Context.Cloud.RoleInstance = CloudRoleInstance;
    }
}