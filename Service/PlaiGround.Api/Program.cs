using System.Reflection;
using System.Text.Json.Serialization;
using Azure.Storage.Blobs;
using Azure.Storage.Blobs.Models;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.HttpLogging;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Authorization;
using Microsoft.Azure.Cosmos;
using Microsoft.Identity.Web;
using Microsoft.OpenApi.Models;
using Newtonsoft.Json;
using PlaiGround.Api.Auth;
using PlaiGround.Api.Events;
using PlaiGround.Api.Options;
using PlaiGround.Api.Services;
using PlaiGround.Api.Telemetry;
using StackExchange.Redis;

var builder = WebApplication.CreateBuilder(args);

// If personal settings is set, load them
var configDisplayOptions = builder.Configuration.GetSection(ConfigDisplayOptions.Section).Get<ConfigDisplayOptions>();
if (builder.Environment.IsDevelopment() && configDisplayOptions?.ShowConfigurationDebugView == true)
{
    // TODO: Use Logger<Program> similar to below
    Console.WriteLine(builder.Configuration.GetDebugView());
}

// https://docs.microsoft.com/en-us/aspnet/core/security/cors?view=aspnetcore-6.0
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy.AllowAnyHeader()
            .AllowAnyMethod()
            .AllowAnyOrigin();
    });
});

// https://docs.microsoft.com/en-us/azure/active-directory/develop/web-api-quickstart?view=aspnetcore-6.0&pivots=devlang-aspnet-core#startup-class
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddMicrosoftIdentityWebApi(builder.Configuration.GetSection(AuthenticationOptions.Section));

// Add API Key based authentication
// TODO: This was temporary until we get client-crendentials auth working
builder.Services.AddAuthentication()
    .AddScheme<ApiKeyAuthenticationHandlerOptions, ApiKeyAuthenticationHandler>(ApiKeyAuthenticationHandler.AuthenticationScheme, authenticationHandlerOptions =>
    {
        // Transfer options from appSettings to authentication handler options
        var authOptions = builder.Configuration.GetSection(ApiKeyAuthenticationOptions.Section).Get<ApiKeyAuthenticationOptions>();
        var keyToIdentity = ApiKeyAuthenticationOptions.GetKeyToIdentityMapFromConfig(authOptions);

        authenticationHandlerOptions.HeaderName = authOptions.HeaderName;
        authenticationHandlerOptions.IdentityClaim = authOptions.IdentityClaim;
        authenticationHandlerOptions.KeyToIdentity = keyToIdentity;
    });

// https://docs.microsoft.com/en-us/aspnet/core/host-and-deploy/health-checks?view=aspnetcore-6.0
builder.Services.AddHealthChecks();

// https://docs.microsoft.com/en-us/aspnet/core/mvc/controllers/filters?msclkid=068f4420d09711ecba4051006120333f&view=aspnetcore-6.0#filter-scopes-and-order-of-execution
builder.Services.AddControllers(options =>
{
    var policy = new AuthorizationPolicyBuilder(JwtBearerDefaults.AuthenticationScheme, ApiKeyAuthenticationHandler.AuthenticationScheme)
        .RequireAuthenticatedUser()
        .Build();

    options.Filters.Add(new AuthorizeFilter(policy));
})
.AddJsonOptions(options =>
{
    var enumConverter = new JsonStringEnumConverter();
    options.JsonSerializerOptions.Converters.Add(enumConverter);
});

builder.Services.Configure<RouteOptions>(options =>
{
    options.LowercaseUrls = true;
    options.LowercaseQueryStrings = true;
});

builder.Services.AddApiVersioning(options =>
{
    options.ReportApiVersions = true;
    options.DefaultApiVersion = new ApiVersion(1, 0);
    options.AssumeDefaultVersionWhenUnspecified = true;
});

builder.Services.AddVersionedApiExplorer(o =>
{
    o.GroupNameFormat = "'v'VVV";
    o.SubstituteApiVersionInUrl = true;
});

builder.Services.AddSwaggerGen(options =>
{
    options.EnableAnnotations(enableAnnotationsForInheritance: true, enableAnnotationsForPolymorphism: true);
    options.UseOneOfForPolymorphism();
    options.DocumentFilter<AddEventModels<BaseEvent>>();
    options.SchemaFilter<ExtendEventSchemas<BaseEvent>>();

    // add XML comments to generated swagger.json
    var xmlFilename = $"{Assembly.GetExecutingAssembly().GetName().Name}.xml";
    options.IncludeXmlComments(Path.Combine(AppContext.BaseDirectory, xmlFilename));

    // TODO: Get versions and generate swagger for each version
    var versions = new List<string> { "v1" };

    foreach (var version in versions)
    {
        var openApiInfo = new OpenApiInfo
        {
            Version = version,
            Title = "PlaiGround API",
            Description = "Service for performing and collecting data on grounded language tasks.",
            Contact = new OpenApiContact
            {
                Name = "Microsoft Deep Learning Engineering Team",
                // TODO: This url should change, deep learning group is larger scope than engineering team
                Url = new Uri("https://www.microsoft.com/en-us/research/group/deep-learning-group/")
            },
        };

        options.SwaggerDoc(version, openApiInfo);
    }

    // Security name must not contain spaces: Adhere to regex: ^[a-zA-Z0-9\.\-_]+$
    var securityDefinitionNameJwt = "JwtBearerToken";
    // https://github.com/domaindrivendev/Swashbuckle.AspNetCore/blob/master/README.md#add-security-definitions-and-requirements-for-bearer-auth
    options.AddSecurityDefinition(securityDefinitionNameJwt, new OpenApiSecurityScheme
    {
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        Description = "JWT Authorization header using the Bearer scheme."
    });

    options.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference {
                    Type = ReferenceType.SecurityScheme,
                    Id = securityDefinitionNameJwt
                }
            },
            new[] { "Read.All" }
        }
    });

    // https://swagger.io/specification/#securityRequirementObject
    // https://swagger.io/specification/#components-security-schemes
    var securityDefinitionNameApiKey = "ApiKey";
    var authOptions = builder.Configuration.GetSection(ApiKeyAuthenticationOptions.Section).Get<ApiKeyAuthenticationOptions>();
    if (string.IsNullOrEmpty(authOptions?.HeaderName))
    {
        throw new InvalidProgramException($"The configuration value ApiKeyAuthentication:HeaderName is not defined. You must set value for this since it effects swagger generation");
    }

    options.AddSecurityDefinition("ApiKey", new OpenApiSecurityScheme
    {
        Type = SecuritySchemeType.ApiKey,
        Scheme = "apiKey",
        Name = authOptions.HeaderName,
        In = ParameterLocation.Header,
        Description = $"API Key provided in the header {authOptions.HeaderName}"
    });

    options.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference {
                    Type = ReferenceType.SecurityScheme,
                    Id = securityDefinitionNameApiKey
                }
            },
            new List<string>()
        }
    });
});

builder.Services.AddW3CLogging(logging =>
{
    // TODO: Investigate which properties we want logged.
    // Assigning all properties does not have expected effect on logs.
    // https://docs.microsoft.com/en-us/aspnet/core/fundamentals/w3c-logger/?view=aspnetcore-6.0#loggingfields
    logging.LoggingFields = W3CLoggingFields.All;
});

// Add Cosmos DB client
builder.Services.AddSingleton(serviceProvider =>
{
    var cosmosOptions = builder.Configuration.GetSection(CosmosOptions.Section).Get<CosmosOptions>();
    var cosmosClientOptions = new CosmosClientOptions
    {
        SerializerOptions = new CosmosSerializationOptions
        {
            PropertyNamingPolicy = CosmosPropertyNamingPolicy.CamelCase
        }
    };

    var cosmosClient = new CosmosClient(cosmosOptions.AccountEndpoint, cosmosOptions.Key, cosmosClientOptions);

    return cosmosClient;
});

builder.Services.AddApplicationInsightsTelemetry();
builder.Services.AddSingleton(serviceProvider =>
{
    var applicationInsightsOptions = builder.Configuration.GetSection(ApplicationInsightsOptions.Section).Get<ApplicationInsightsOptions>();
    return new CloudNameTelemetry()
    {
        CloudRoleName = applicationInsightsOptions.CloudRoleName,
        CloudRoleInstance = applicationInsightsOptions.CloudRoleName
    };
});

builder.Services.Configure<ApiKeyAuthenticationOptions>(builder.Configuration.GetSection(ApiKeyAuthenticationOptions.Section));
builder.Services.Configure<ApplicationInsightsOptions>(builder.Configuration.GetSection(ApplicationInsightsOptions.Section));
builder.Services.Configure<CosmosOptions>(builder.Configuration.GetSection(CosmosOptions.Section));
builder.Services.Configure<EventHubOptions>(builder.Configuration.GetSection(EventHubOptions.Section));
builder.Services.Configure<StorageAccountOptions>(builder.Configuration.GetSection(StorageAccountOptions.Section));
builder.Services.Configure<RedisOptions>(builder.Configuration.GetSection(RedisOptions.Section));

// Add Storage Account client
builder.Services.AddSingleton(serviceProvider =>
{
    var storageAccountOptions = builder.Configuration.GetSection(StorageAccountOptions.Section).Get<StorageAccountOptions>();
    var blobServiceClient = new BlobServiceClient(storageAccountOptions.ConnectionString);

    return blobServiceClient;
});

// Add Redis Client
builder.Services.AddSingleton(serviceProvider =>
{
    var redisOptions = builder.Configuration.GetSection(RedisOptions.Section).Get<RedisOptions>();
    var redisConfig = ConfigurationOptions.Parse(redisOptions.Endpoint);
    var redisClient = ConnectionMultiplexer.Connect(redisConfig);

    return redisClient;
});

builder.Services.AddScoped<IEvaluationService, EvaluationService>();
// EventConversionService must be singeton because it is consumed by EventService which is also singleton
builder.Services.AddSingleton<IEventConversionService, EventConversionService>();
builder.Services.AddSingleton<IEventsService, EventsService>();
builder.Services.AddScoped<ITeamsService, TeamsService>();
builder.Services.AddScoped<IJoinRequestsService, JoinRequestsService>();
builder.Services.AddScoped<IAgentsService, AgentsService>();
builder.Services.AddScoped<IHumanChallengesService, HumanChallengesService>();
builder.Services.AddScoped<IAgentChallengesService, AgentChallengesService>();
builder.Services.AddScoped<ITournamentsService, TournamentsService>();
builder.Services.AddScoped<ITasksService, TasksService>();
builder.Services.AddScoped<IGamesService, GamesService>();
builder.Services.AddScoped<IOwningTeamMemberAuthorizationService, OwningTeamMemberAuthorizationService>();

// Prevent DateParsing behavior of Newtonsoft when using JsonConvert
// https://github.com/JamesNK/Newtonsoft.Json/issues/862#issuecomment-237487551
JsonConvert.DefaultSettings = () => new JsonSerializerSettings
{
    DateParseHandling = DateParseHandling.None
};

var app = builder.Build();

// If the DLL is invoked by Swagger dotnet tool the following arguments will be passed
// ["--applicationName=PlaiGround.Api, Version=1.0.0.0, Culture=neutral, PublicKeyToken=null"]
// If there is normal execution such as dotnet run, no arguments are passed. (args is []).
// If any of the arguments contain value sent by the swagger CLI then assume it was run by swagger CLI. (May lead to false positives) 
var isProgramExecutedFromSwaggerCli = args.Aggregate(false, (aggregate, arg) => aggregate || arg.Contains("applicationName"));

// If not executing DLL directly via Swagger CLI then we can execute code which calls to external resources.
// Resolve dependency at startup, perform expensive operations such as creating Cosmos and Blob containers required for application use.
// https://docs.microsoft.com/en-us/aspnet/core/fundamentals/dependency-injection?view=aspnetcore-6.0#resolve-a-service-at-app-start-up
if (!isProgramExecutedFromSwaggerCli)
{
    using (var serviceScope = app.Services.CreateScope())
    {
        var logger = serviceScope.ServiceProvider.GetRequiredService<ILogger<Program>>();

        logger.LogInformation($"Create Cosmos resources");
        var cosmosOptions = builder.Configuration.GetSection(CosmosOptions.Section).Get<CosmosOptions>();
        var containerNamesToPartitionKeys = new List<(string, string)> {
            (TournamentsService.ContainerName, "/id"),
            (TasksService.ContainerName, "/tournamentId"),
            (GamesService.ContainerName, "/taskId"),
            (TeamsService.ContainerName, "/id"),
            (JoinRequestsService.ContainerName, "/teamId"),
            (AgentsService.ContainerName, "/id"),
            (HumanChallengesService.ContainerName, "/tournamentId"),
            (EventsService.ContainerName, "/taskId"),
            (AgentChallengesService.ContainerName, "/tournamentId"),
        };

        var cosmosClient = serviceScope.ServiceProvider.GetRequiredService<CosmosClient>();

        logger.LogInformation($"If Cosmos database {cosmosOptions.DatabaseName} does not exist, create it");
        var database = await cosmosClient.CreateDatabaseIfNotExistsAsync(cosmosOptions.DatabaseName);

        foreach (var (containerName, partitionKey) in containerNamesToPartitionKeys)
        {
            logger.LogInformation($"If Cosmos container {containerName} (partitionKey: {partitionKey}) does not exist, create it");
            await database.Database.CreateContainerIfNotExistsAsync(containerName, partitionKey);
        }

        logger.LogInformation($"Create Blob Storage containers");
        var storageAccountOptions = app.Configuration.GetSection(StorageAccountOptions.Section).Get<StorageAccountOptions>();
        var blobServiceClient = serviceScope.ServiceProvider.GetRequiredService<BlobServiceClient>();
        var humanChallengeBlobContainerClient = blobServiceClient.GetBlobContainerClient(storageAccountOptions.HumanChallengeDataContainerName);

        // TODO: Would be more secure to generate SAS Url each time download was requested instead of making all blobs accessible
        // Each blob will have guid in name to prevent unkown access although IDs could be known
        logger.LogInformation($"If blob container {storageAccountOptions.HumanChallengeDataContainerName} does not exist, create it");
        await humanChallengeBlobContainerClient.CreateIfNotExistsAsync(PublicAccessType.BlobContainer);

        var agentChallengeBlobContainerClient = blobServiceClient.GetBlobContainerClient(storageAccountOptions.AgentChallengeDataContainerName);
        logger.LogInformation($"If blob container {storageAccountOptions.AgentChallengeDataContainerName} does not exist, create it");
        await agentChallengeBlobContainerClient.CreateIfNotExistsAsync(PublicAccessType.BlobContainer);

        var taskDataBlobContainerClient = blobServiceClient.GetBlobContainerClient(storageAccountOptions.TaskDataContainerName);
        logger.LogInformation($"If blob container {storageAccountOptions.TaskDataContainerName} does not exist, create it");
        await taskDataBlobContainerClient.CreateIfNotExistsAsync(PublicAccessType.BlobContainer);

        var gameDataBlobContainerClient = blobServiceClient.GetBlobContainerClient(storageAccountOptions.GameDataContainerName);
        logger.LogInformation($"If blob container {storageAccountOptions.GameDataContainerName} does not exist, create it");
        await gameDataBlobContainerClient.CreateIfNotExistsAsync(PublicAccessType.BlobContainer);

        var redisClient = serviceScope.ServiceProvider.GetRequiredService<ConnectionMultiplexer>();
        logger.LogInformation($"Test Redis Connection at endpoints: {string.Join(", ", redisClient.GetEndPoints().Select(e => e.ToString()))}");
        var db = redisClient.GetDatabase();
        var pingResponse = await db.PingAsync();
        logger.LogInformation($"Pinged Redis in {pingResponse.Milliseconds} ms");
    }
}

if (app.Environment.IsDevelopment())
{
    app.UseDeveloperExceptionPage();
    app.UseSwagger();
    app.UseSwaggerUI(options =>
    {
        options.SwaggerEndpoint("/swagger/v1/swagger.json", "v1");
        options.RoutePrefix = string.Empty;
    });
}

app.MapHealthChecks("/health");

app.UseW3CLogging();

// Can still use HTTPS but, forced redirect is disabled so local Minecraft Plugin can communication with Service
// TODO: Find how to read self-signed certification from local java to allow HTTPS communication
//app.UseHttpsRedirection();

app.UseCors();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();
