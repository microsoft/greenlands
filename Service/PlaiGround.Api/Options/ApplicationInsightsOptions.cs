namespace PlaiGround.Api.Options;

public class ApplicationInsightsOptions
{
    public const string Section = "ApplicationInsights";

    public string ConnectionString { get; init; }

    public string CloudRoleName { get; init; }

    public string CloudRoleInstance { get; init; }
}
