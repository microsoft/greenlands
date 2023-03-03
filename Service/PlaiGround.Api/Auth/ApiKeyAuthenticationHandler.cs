using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;
using System.Security.Claims;
using System.Text.Encodings.Web;

namespace PlaiGround.Api.Auth;

public class ApiKeyAuthenticationHandler : AuthenticationHandler<ApiKeyAuthenticationHandlerOptions>
{
    public static readonly string AuthenticationScheme = "ApiKey";

    private readonly ApiKeyAuthenticationHandlerOptions _apiKeyAuthenticationHandlerOptions;

    public ApiKeyAuthenticationHandler(
        IOptionsMonitor<ApiKeyAuthenticationHandlerOptions> options,
        ILoggerFactory logger,
        UrlEncoder encoder,
        ISystemClock clock,
        IConfigureOptions<ApiKeyAuthenticationHandlerOptions> config
    ) : base(options, logger, encoder, clock)
    {
        // Note: I'm not sure why this check is required to set the options but I saw technique used here.
        // https://youtu.be/plSTSM2OpZs?t=50
        // https://stackoverflow.com/questions/68788196/why-configure-options-in-authenticationbuilder-addschemetoptions-thandler-doe?msclkid=9d4ac7acd09511ecbe409bf4a4f78992
        // If current options object doesn't have header set, explicitly call configure to set it
        if (string.IsNullOrEmpty(options.CurrentValue.HeaderName))
        {
            var optionConfig = config as IConfigureNamedOptions<ApiKeyAuthenticationHandlerOptions>;
            optionConfig!.Configure(AuthenticationScheme, options.CurrentValue);
        }

        _apiKeyAuthenticationHandlerOptions = options.CurrentValue;
    }

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var apiKeyHeaderName = _apiKeyAuthenticationHandlerOptions.HeaderName;
        var apiKeyHeaderValue = Request.Headers[apiKeyHeaderName].FirstOrDefault();

        if (string.IsNullOrWhiteSpace(apiKeyHeaderValue))
        {
            return Task.FromResult(AuthenticateResult.Fail($"Header: {apiKeyHeaderName} was not set, but must be provided."));
        }

        var identity = _apiKeyAuthenticationHandlerOptions.KeyToIdentity.GetValueOrDefault(apiKeyHeaderValue);
        if (string.IsNullOrWhiteSpace(identity))
        {
            return Task.FromResult(AuthenticateResult.Fail($"The value for the {apiKeyHeaderName} provided it not map to any known identity. Please check the API key."));
        }

        var claimsPrincipal = new ClaimsPrincipal(new List<ClaimsIdentity>
        {
            new ClaimsIdentity(new List<Claim>
            {
                new Claim(_apiKeyAuthenticationHandlerOptions.IdentityClaim, identity)
            }, nameof(ApiKeyAuthenticationHandler))
        });

        var authenticationTicket = new AuthenticationTicket(claimsPrincipal, new AuthenticationProperties(), AuthenticationScheme);

        return Task.FromResult(AuthenticateResult.Success(authenticationTicket));
    }
}

public class ApiKeyAuthenticationHandlerOptions : AuthenticationSchemeOptions
{
    public string HeaderName { get; set; }

    public string IdentityClaim { get; set; }

    public Dictionary<string, string> KeyToIdentity { get; set; } = new Dictionary<string, string>();
}