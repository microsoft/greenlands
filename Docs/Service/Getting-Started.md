# Getting Started

1. Open `Service.sln`
1. Configure Developer [Application Settings](#Application-Settings)
1. Add necessary Secrets \
    See the [Adding Secrets](#Adding-Secrets) section for instructions.
1. Run `Greenlands.Api` \
    ![start](https://user-images.githubusercontent.com/2856501/145110249-a4e527e6-90e6-4f6a-9e32-1a4760217c95.png)


## Core Concepts

The service manages these different models:

- Models Services (Agents)
    - These are registered by the researchers, once validated they are made available to be consumed during sessions / games
- Tasks
    - Tasks have an ID
    - The tasks type controls action space of the builder
        - For example, only allow placing or removing blocks of at specific positions within a build region.
- Sessions (Games)
    - These are the association between Architects and Builders (the players), and Tasks
    - As the game progress turns are added
- Turns
    - These are a record of each change of session / game state to re-evaluation completion
        - For example, each time the architect gives an instruction to the builder, an architect turn is added. Then after the builder places or removes blocks, a builder turn is added.


## Data Structure

[![](https://mermaid.ink/img/pako:eNrFF8ty2jDwVzQ-Jz_gG68mzIQmLTC9cFHsjdFEljySTMIA_96V5JeMCbQ91Adb7Ev73uUQJTKFKI5ATRnNFM03YiMIPg80B3I83t8fj2RF9TuJicHPPA0IDv5sH20UExmxBD3QquKrwVNqYMWQe6IAjxXm5D_-7W78WviwlACzLtIQU4n5jqq3QHvX0iAhce8WYS30iLlghlHeAFqaJ6aNBU-2VGSAslQGHYBuKUWZv4Iis88CElRqWipqmBRLSKRIdeODyn5ZKoFShLnRxUDz-VVDG6GD5tbSBR7KxOqmQztb_p-SA7EvXStsn1cpOVlrWFCTbCH9JRVPx1wm73q2o7ykRqqLxC-c7kHNxQ6lS7W_leFR8vRW2ieZOI9_QT8S-gMUpD9K0FdIvcyJFG9M5UNyh2Lp68kGC8vp-UOgxU09OehNwQ6j-jd1YKO59MIWYNNyfpaBHv6nCp0Ca5y5h1pU3GTpsF8OpJdgcZhiPezhil8q4BR0olhhAzRUCVbUhBb0lXEscdCk--MSw8hXBxn1q8Qlx4QKSzT2KZIuSm5YwV0kdNhcFth7m8PFrHHW1p34suLxgOq3CKmNiVtzhpiDuw7nJo9hCSDG-2ezBeWLY8AxSNO2yE7_uWB2rdvAfXhFAr67DPgfcrm7iF2CSFfwaRagNc3gnKCq5AHMSmYZh2-cZVvT19yOrJPsedhmffOjzf0x1TCzva7mcNM0Jhl-hqjOk93BV_sCbinQh0rslcFcgwOFa6S7cImoBCeH-5xfo2RZzIfm-SizzKB2LLlxaXAcOEQ5B5yjV7vgWjEyE2khmTAh1Os6wQpbK96iuhpZN5LWl01IA62PPlChXrZqzhT1zI9lTkVL-C9jfDggg37odHYf3U7-o2vIRObYi3AY2JTQL6AsVUsSKu3XBB2uCTfMGwxEF26jMJUfgkua4rnn5J6f6lHgV87Ghk5A_qdPQ2UD7zorqaEvCgpa7XZu8ujG5i7ZEkyTj6cL9lW-6HkoPtciuotywDWEpbjKO69sIuzDaEQU4zGFN4pTaBNtxAlJS7cOzFKGu0oU47IHdxEtjVzuRRLFb5RrqImqPwUV9PQbJszpzg)](https://mermaid.live/edit#pako:eNrFF8ty2jDwVzQ-Jz_gG68mzIQmLTC9cFHsjdFEljySTMIA_96V5JeMCbQ91Adb7Ev73uUQJTKFKI5ATRnNFM03YiMIPg80B3I83t8fj2RF9TuJicHPPA0IDv5sH20UExmxBD3QquKrwVNqYMWQe6IAjxXm5D_-7W78WviwlACzLtIQU4n5jqq3QHvX0iAhce8WYS30iLlghlHeAFqaJ6aNBU-2VGSAslQGHYBuKUWZv4Iis88CElRqWipqmBRLSKRIdeODyn5ZKoFShLnRxUDz-VVDG6GD5tbSBR7KxOqmQztb_p-SA7EvXStsn1cpOVlrWFCTbCH9JRVPx1wm73q2o7ykRqqLxC-c7kHNxQ6lS7W_leFR8vRW2ieZOI9_QT8S-gMUpD9K0FdIvcyJFG9M5UNyh2Lp68kGC8vp-UOgxU09OehNwQ6j-jd1YKO59MIWYNNyfpaBHv6nCp0Ca5y5h1pU3GTpsF8OpJdgcZhiPezhil8q4BR0olhhAzRUCVbUhBb0lXEscdCk--MSw8hXBxn1q8Qlx4QKSzT2KZIuSm5YwV0kdNhcFth7m8PFrHHW1p34suLxgOq3CKmNiVtzhpiDuw7nJo9hCSDG-2ezBeWLY8AxSNO2yE7_uWB2rdvAfXhFAr67DPgfcrm7iF2CSFfwaRagNc3gnKCq5AHMSmYZh2-cZVvT19yOrJPsedhmffOjzf0x1TCzva7mcNM0Jhl-hqjOk93BV_sCbinQh0rslcFcgwOFa6S7cImoBCeH-5xfo2RZzIfm-SizzKB2LLlxaXAcOEQ5B5yjV7vgWjEyE2khmTAh1Os6wQpbK96iuhpZN5LWl01IA62PPlChXrZqzhT1zI9lTkVL-C9jfDggg37odHYf3U7-o2vIRObYi3AY2JTQL6AsVUsSKu3XBB2uCTfMGwxEF26jMJUfgkua4rnn5J6f6lHgV87Ghk5A_qdPQ2UD7zorqaEvCgpa7XZu8ujG5i7ZEkyTj6cL9lW-6HkoPtciuotywDWEpbjKO69sIuzDaEQU4zGFN4pTaBNtxAlJS7cOzFKGu0oU47IHdxEtjVzuRRLFb5RrqImqPwUV9PQbJszpzg)


## Application Settings

In Greenlands service we use Azure Event Hub and Azure Stream Analytics. Both of these resources do not have local emulators so to get full functionality of the service it must be connected to remote resources in Azure. During development we still want isolation so that breaking changes such as changing the schema of objects in database does not effect other developers. To achieve this isolation we use developer specific resources, application settings, and keys.

The services uses the following resources:

- **Cosmos Db**
  - appsettings specify Cosmos Account and Database Id
  - code creates database and containers if they don't exist on startup
  - Action: Update "CosmosDb:DatabaseName" in appsettings.Development.json
- **Blob Storage**
  - appsettings specify the Blob Storage account and Container names
  - code creates containers if they don't exist
  - Action: Update "StorageAccount:HumanChallengeDataContainerName" in appsettings.Development.json
- **Event Hub**
  - appsettings specify the Event Hub namespace and EventHub name
    - These resources are not created programmatically and must be created manually.
- **Service Bus**
  - appsettings specify the Service Bus namespace, topic, and subscription
    - The topic is created during Bicep deployment AND during service startup
    - The subscription is created dynamically when an Agent is added
- **Stream Analytics**
  - This resource cannot be created programmatically and must also be created manuallly.
- **Azure Cache for Redis**
  - appsetting specify the Redis Endpoint, Key Prefixes, and Key Expiration


## Adding Secrets

The service connects to various services in Azure and this communication is secured by using keys. When Service is deployed to Azure, secrets are made available by configuration setting in the Azure portal or Azure Dev Ops release. However, when running locally, the secrets are made available by using secret management in through Visual Studio. This is a local settings file linked to your project that is merged into the appSettings.json files so it can be used in the same way as the secrets in Azure.

Steps to add secrets:

Docs: https://docs.microsoft.com/en-us/aspnet/core/security/app-secrets?view=aspnetcore-6.0&tabs=windows#enable-secret-storage

1. Create user secrets file
    - dotnet CLI: `dotnet user-secrets init`
    - Visual Studio:
      Right-Click the Greenlands.Api project and select "Manage User Secrets"

      ![image](https://user-images.githubusercontent.com/2856501/150392370-292bc3e0-1a2c-45e0-9a4f-e011158e793b.png)

      This should create a .json file at location such as \
      `~\AppData\Roaming\Microsoft\UserSecrets\some guid\secrets.json`

1. Add appropriate config sections such as "EventHub", with property "NamespaceSendListenSharedAccessKey" and value (actual secret value) from portal to the file.
    - dotnet CLI: `dotnet user-secrets set EventHub:NamespaceSendListenSharedAccessKey <your secret value>`
    - Visual Studio:
      Right-Click the Greenlands.Api project and select "Manage User Secrets"
      Select "Add New" and enter "EventHub:SharedAccessKey" and the actual secret value.
    Example schema below with keys removed.

    ```json
    {
      "StorageAccount:ConnectionString": "<Removed>",
      "CosmosDb:Key": "<Removed>",
      "ApiKeyAuthentication:PluginKey": "<Removed>",
      "ApplicationInsights:ConnectionString": "<Removed>",
      "Redis:Endpoint": "<Removed>",
      "EventHub:NamespaceSendListenSharedAccessKey": "<Removed>",
      "EventHub:EventHubSendListenSharedAccessKey": "<Removed>"
    }
    ```

In development we currently use the following secrets:

1. Application Insights Connection String
1. API Key required for the Plugin
1. Cosmos Account information
1. Connection String storage Account
1. Connection String for Azure Redis
1. SAS sendlisten Key for the Event Hub Namespace
1. SAS hubsendlisten key for Event Hub


## Generating clients for the Greenlands Service API

There is a script inside the `ClientGeneration` directory that will generate the client for you using our default options. To execute it just do:

```powershell
cd ClientGeneration
pwsh ./generate-clients.ps1 -Install
```

Once the script is finished you should have a `*Client` folder for each target language.

The script optionally accepts a _version_ parameter, that specifies which version should the generated projects be tagged with, this can be specified by:

```powershell
.\generate-clients.ps1 -ClientVersion custom-version-here
```

If no version is specified then a default of `1.0.0-LOCAL` will be used.
