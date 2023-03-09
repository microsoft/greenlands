using Microsoft.ApplicationInsights.Channel;
using Microsoft.ApplicationInsights.Extensibility;

namespace Greenlands.Api.Telemetry;

public class CloudNameTelemetry : ITelemetryInitializer
{
    public string CloudRoleName { get; init; }

    public string CloudRoleInstance { get; init; }

    public CloudNameTelemetry(string cloudRoleName, string cloudRoleInstance) {
        CloudRoleName = cloudRoleName;
        CloudRoleInstance = cloudRoleInstance;
    }

    public void Initialize(ITelemetry telemetry)
    {
        telemetry.Context.Cloud.RoleName = CloudRoleName;
        telemetry.Context.Cloud.RoleInstance = CloudRoleInstance;
    }
}