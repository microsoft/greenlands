namespace PlaiGround.Api.Evaluators;

/// <summary>
/// Evaluation score is the percentage of blocks that are in their expected position
/// </summary>
public class MatchedWorldBlocksEvaluator : Evaluator
{
    public override Task<double> EvaluateWithoutBoundaryCheck(
        IList<GameChanges> targetGameChangesList,
        GameChanges currentGameChanges,
        GameState initialGameState
    )
    {
        // TODO: Determine how to evaluate currentChanges against each intermediate GameChange in list
        var targetGameChanges = targetGameChangesList.Last();
        var totalBlockChangesCount = targetGameChanges.WorldChanges?.BlockChanges?.Count ?? 0;
        if (totalBlockChangesCount == 0)
        {
            throw new InvalidOperationException($"You attempted to evaluate a game using Matched World Blocks on a task that has no expected block changes. Please update the Task and retry the game.");
        }

        var currentBlockChanges = currentGameChanges.WorldChanges?.BlockChanges ?? new Dictionary<string, Block>();

        // Ensure unique locations of block changes and associate an IsMatched boolean with each block change initialized to false.
        var currentMatchedBlockChanges = currentBlockChanges
            .ToDictionary(bc => bc.Key, bc => new BlockChangeMatch { Block = bc.Value, IsMatched = false });

        var targetBlockChanges = targetGameChanges.WorldChanges?.BlockChanges ?? new Dictionary<string, Block>();

        // For each target block location, if the current block locations have a block of matching type, then set is matched to true
        foreach (var (locationString, targetBlock) in targetBlockChanges)
        {
            var blockAtLocationInCurrentState = currentMatchedBlockChanges.GetValueOrDefault(locationString);
            if (blockAtLocationInCurrentState != null && blockAtLocationInCurrentState.Block == targetBlock)
            {
                blockAtLocationInCurrentState.IsMatched = true;
            }
        }

        var percentageMatchedBlocks = GetPercentageMatchedBlocks(currentMatchedBlockChanges.Values.ToList(), targetBlockChanges.Count);

        return Task.FromResult(percentageMatchedBlocks);
    }

    /// <summary>
    /// Given list of current block changes with is matched and the target block change return the percentage of matched blocks against the target.
    /// 
    /// In the case that the world has FEWER block changes than expected we simply divide the number of matched blocks by the total expected blocks
    /// (Assume letters for locations)
    /// 
    /// Current: [(A,1,true), (B,1,true), (C,7,true)]
    /// Target:  [(A,1), (B,1), (C,3), (D,4)]
    /// 
    /// Result: 3 changes with 2 matched, out of 4 expected. (2/4) = 0.5
    /// 
    /// In the case that the world has MORE block changes than expected we divide the matched blocks by the number of changed blocks.
    /// 
    /// Current: [(A,1,true), (B,1,true), (C,7,false)]
    /// Target:  [(A,1), (B,1)]
    /// 
    /// Result: 3 changes with complete 2 matched, but there was only supposed to be 2 changes. (2/3) = 0.6666
    /// 
    /// This penalizes players for changing more of the world than is necessary.
    /// </summary>
    public static double GetPercentageMatchedBlocks(
        IList<BlockChangeMatch> currentBlockChangeMatches,
        int expectedMatchedBlocks
    )
    {
        double percentageMatchedBlocks;
        var matchedBlocks = currentBlockChangeMatches.Where(bcm => bcm.IsMatched).ToList();

        if (currentBlockChangeMatches.Count <= expectedMatchedBlocks)
        {
            percentageMatchedBlocks = (double)matchedBlocks.Count / expectedMatchedBlocks;
        }
        else
        {
            // We know that excess block changes CANNOT match a target block so we divide by total count of itself
            // This reduces the percentage match the more blocks they have that are not expected.
            percentageMatchedBlocks = (double)matchedBlocks.Count / currentBlockChangeMatches.Count;
        }

        return percentageMatchedBlocks;
    }
}

public class BlockChangeMatch
{
    public Block Block { get; init; }

    public bool IsMatched { get; set; }
}
