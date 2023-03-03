namespace PlaiGround.Api.Evaluators;

/// <summary>
/// Evaluation score is the percentage of matched inventory items across all roles.
/// 
/// Example: Role 1 (3/4) + Role 2 (2/2) = 7/8 = 0.875
/// </summary>
public class MatchedPlayerInventoryEvaluator : IEvaluator
{
    public Task<double> Evaluate(
        IList<GameChanges> targetGameChangesList,
        GameChanges currentGameChanges,
        GameState initialGameState
    )
    {
        // TODO: Determine how to evaluate currentChanges against each intermediate GameChange in list
        var targetGameChanges = targetGameChangesList.Last();

        // This is a dictionary of roleIds to expected item counts to current counts
        // {
        //   "role1": {
        //     3: (4, 1),  // Role has only 1 of expected 4 items of type 3
        //   },
        //   "role2": {
        //     5: (2, 2),  // Role has 2 of 2 expected items of type 5
        //   }
        // }
        var roleToItemTypesToExpectedCountAndCurrentCount = new Dictionary<RoleId, IDictionary<ItemType, (int expectedCount, int currentCount)>>();

        // For each role with expected inventory changes add the number of changes to the total
        foreach (var (roleId, playerChanges) in (targetGameChanges.PlayerChanges ?? new Dictionary<RoleId, PlayerChanges>()))
        {
            var roleItemTypesToExpectedCountAndCurrentCount = new Dictionary<ItemType, (int expectedCount, int currentCount)>();
            var expectedInventoryChangesForRole = playerChanges?.InventoryChanges ?? new Dictionary<ItemType, ItemQuantity>();
            var currentInventoryChanges = currentGameChanges.PlayerChanges?.GetValueOrDefault(roleId)?.InventoryChanges ?? new Dictionary<ItemType, ItemQuantity>();

            // For each expected inventory change, add itemType, expected count, and current count
            foreach (var (expectedEntityType, expectedEntityCount) in expectedInventoryChangesForRole)
            {
                var currentEntityCount = currentInventoryChanges.GetValueOrDefault(expectedEntityType);

                roleItemTypesToExpectedCountAndCurrentCount[expectedEntityType] = (expectedEntityCount, currentEntityCount);
            }

            if (roleItemTypesToExpectedCountAndCurrentCount.Any())
            {
                roleToItemTypesToExpectedCountAndCurrentCount[roleId] = roleItemTypesToExpectedCountAndCurrentCount;
            }
        }

        if (roleToItemTypesToExpectedCountAndCurrentCount.Count == 0)
        {
            throw new InvalidOperationException($"You attempted to evaluate a game using Matched Inventory on a task that has no expected inventory changes. Please update the Task and retry the game.");
        }

        var matchedInventoryPerItem = new List<double>();
        foreach (var (roleId, itemTypesToExpectedCountAndCurrentCount) in roleToItemTypesToExpectedCountAndCurrentCount)
        {
            foreach (var (itemType, (expectedCount, currentCount)) in itemTypesToExpectedCountAndCurrentCount)
            {
                var matchPercentage = GetMatchPercentage(currentCount, expectedCount);

                matchedInventoryPerItem.Add(matchPercentage);
            }
        }

        var percentageMatchedInventory = matchedInventoryPerItem.Average();

        return Task.FromResult(percentageMatchedInventory);
    }

    /// <summary>
    /// Get the match percentage of current vs expected.
    /// 
    /// Normally player will have less inventory than expected and there is linear relationship.
    /// If player has more inventory of type than expected, simulate linear relationship down to 0 then stay at 0 for any excess.
    /// 
    /// This means having slightly less or slightly more will be equivalently penalized so that model has more continuity in data.
    /// </summary>
    public static double GetMatchPercentage(int currentCount, int expectedCount)
    {
        double matchPercentage;

        if (currentCount <= expectedCount)
        {
            matchPercentage = (double)currentCount / expectedCount;
        }
        else
        {
            var excessBlocks = currentCount - expectedCount;
            matchPercentage = (double)Math.Max(0, expectedCount - excessBlocks) / expectedCount;
        }

        return matchPercentage;
    }
}
