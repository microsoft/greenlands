namespace PlaiGround.Api.Evaluators;

/// <summary>
/// Evaluation score is the percentage of blocks that are in their expected position
/// </summary>
public abstract class Evaluator : IEvaluator
{
    public abstract Task<double> EvaluateWithoutBoundaryCheck(
        IList<GameChanges> targetGameChangesList,
        GameChanges currentGameChanges,
        GameState initialGameState
    );

    public async Task<double> Evaluate(
        IList<GameChanges> targetGameChangesList,
        GameChanges currentGameChanges,
        GameState initialGameState
    )
    {
        var score = await EvaluateWithoutBoundaryCheck(targetGameChangesList, currentGameChanges, initialGameState);

        if (score < 0)
        {
            throw new InvalidOperationException($"Score must be positive value between 0 and 1. Score {score} was less than 0.");
        }

        if (score > 1)
        {
            throw new InvalidOperationException($"Score must be positive value between 0 and 1. Score {score} was greater than 1.");
        }

        return score;
    }
}
