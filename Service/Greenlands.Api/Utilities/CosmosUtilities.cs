using Microsoft.Azure.Cosmos;
using System.Net;

namespace Greenlands.Api.Utilities;

public class CosmosUtilities
{
    /// <summary>
    /// Read all items in a collection
    /// </summary>
    public static async Task<List<T>> ReadAll<T>(
        Container container,
        QueryDefinition query,
        ILogger logger
        )
    {
        var items = new List<T>();

        using (var queryIterator = container.GetItemQueryIterator<T>(query))
        {
            while (queryIterator.HasMoreResults)
            {
                var feedResponse = await queryIterator.ReadNextAsync();
                items.AddRange(feedResponse);

                if (feedResponse.Diagnostics != null)
                {
                    logger.LogDebug($"QueryWithSqlParameters Diagnostics: {feedResponse.Diagnostics}");
                }
            }
        }

        return items;
    }

    /// <summary>
    /// Search for one item using ReadItemAsync but allow returning null when item is not found.
    /// </summary>
    public static async Task<ItemResponse<T>?> FindItem<T>(Container container, string id, PartitionKey partitionKey)
    {
        try
        {
            return await container.ReadItemAsync<T>(id, partitionKey);
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            return default;
        }
    }
}

