namespace PlaiGround.Api.Options;

public class ApiKeyAuthenticationOptions
{
    public const string Section = "ApiKeyAuthentication";

    public string HeaderName { get; init; }

    public string IdentityClaim { get; init; }

    public string PluginName { get; init; }

    public string PluginKey { get; init; }

    public static Dictionary<string, string> GetKeyToIdentityMapFromConfig(ApiKeyAuthenticationOptions options)
    {
        var keyToIdentityMap = new Dictionary<string, string>();
        keyToIdentityMap[options.PluginKey] = options.PluginName;

        return keyToIdentityMap;
    }
}
