namespace Greenlands.Api.Evaluators;

/// <summary>
/// Evaluation score is average of number roles holding the correct items.
/// Each role may only be 0% or 100% if target block is in hand.
/// </summary>
public class MatchedPlayerHoldEvaluator : Evaluator
{
    public override Task<double> EvaluateWithoutBoundaryCheck(
        IList<GameChanges> targetGameChangesList,
        GameChanges currentGameChanges,
        GameState initialGameState
    )
    {
        // TODO: Determine how to evaluate currentChanges against each intermediate GameChange in list
        var targetGameChanges = targetGameChangesList.Last();
        var roleToMatchedHoldItem = new Dictionary<string, bool>();

        // For each role with expected hold item check if the current role is holding that item.
        foreach (var (roleId, playerChanges) in (targetGameChanges.PlayerChanges ?? new Dictionary<string, PlayerChanges>()))
        {
            var expectedRoleItemType = playerChanges?.CurrentItem?.Type;
            if (expectedRoleItemType != null)
            {
                var currentRoleItemType = currentGameChanges.PlayerChanges?.GetValueOrDefault(roleId)?.CurrentItem?.Type;
                var isCurrentItemTypeMatch = currentRoleItemType == null
                    ? false
                    : expectedRoleItemType == currentRoleItemType;

                roleToMatchedHoldItem[roleId] = isCurrentItemTypeMatch;
            }
        }

        if (roleToMatchedHoldItem.Count == 0)
        {
            throw new InvalidOperationException($"You attempted to evaluate a game using Matched Hold Item on a task that has no expected hold items. Please update the Task and retry the game.");
        }

        var numberOfRolesHoldingCorrectItem = roleToMatchedHoldItem.Values
            .Aggregate(0, (aggregate, isHoldingCorrectItem) => {
                return isHoldingCorrectItem
                    ? aggregate + 1
                    : aggregate;
            });

        var score = (double)numberOfRolesHoldingCorrectItem / roleToMatchedHoldItem.Count;

        return Task.FromResult(score);
    }
}
