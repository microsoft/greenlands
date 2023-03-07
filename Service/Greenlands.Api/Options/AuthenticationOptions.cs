namespace Greenlands.Api.Options;

public class AuthenticationOptions
{
    public const string Section = "Authentication";

    public string Instance { get; init; }
    public string ClientId { get; init; }
    public string TenantId { get; init; }
    public string Scope { get; init; }
}
