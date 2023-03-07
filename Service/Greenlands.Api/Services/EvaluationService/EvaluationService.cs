using Newtonsoft.Json;
using Newtonsoft.Json.Converters;
using Greenlands.Api.Evaluators;

namespace Greenlands.Api.Services;

[JsonConverter(typeof(StringEnumConverter))]
public enum EvaluatorIds
{
    MatchWorldBlocksEvaluator,
    MatchPlayerInventoryEvaluator,
    MatchPlayerLocationEvaluator,
    MatchPlayerHoldEvaluator,
    AnsweredQuestionEvaluator,
    PlayerConfirmationEvaluator,
}

public class EvaluationService : IEvaluationService
{
    private readonly IDictionary<EvaluatorIds, Func<dynamic, IEvaluator>> _evaluators = new Dictionary<EvaluatorIds, Func<dynamic, IEvaluator>>
    {
        { EvaluatorIds.MatchWorldBlocksEvaluator, (evalInput) => new MatchedWorldBlocksEvaluator() },
        { EvaluatorIds.MatchPlayerInventoryEvaluator, (evalInput) => new MatchedPlayerInventoryEvaluator() },
        // If position distance of radius is stored in the task then this evaluator must be created on demand
        { EvaluatorIds.MatchPlayerLocationEvaluator, (evalInput) => new MatchedPlayerLocationEvaluator(evalInput.LocationWithinDistance) },
        { EvaluatorIds.MatchPlayerHoldEvaluator, (evalInput) => new MatchedPlayerHoldEvaluator() },
    };

    private readonly ILogger<EvaluationService> _logger;

    public EvaluationService(ILogger<EvaluationService> logger)
    {
        _logger = logger;
    }

    public object GetEvaluationInput(
        EvaluatorIds evaluatorId,
        Tournament tournament,
        GreenlandsTask task
    )
    {
        switch (evaluatorId)
        {
            case EvaluatorIds.MatchPlayerLocationEvaluator:
                {
                    // TODO: Get value from tournament or task
                    return new { LocationWithinDistance = 10 };
                }
            default:
                {
                    return new { };
                }
        }
    }

    private Func<dynamic, IEvaluator> GetById(EvaluatorIds evaluatorId)
    {
        if (!_evaluators.ContainsKey(evaluatorId))
        {
            throw new KeyNotFoundException($"Evaluator with id {evaluatorId} not found.");
        }

        return _evaluators[evaluatorId];
    }

    public async Task<double> Evaluate(
        EvaluatorIds evaluatorId,
        object evaluatorInput,
        IList<GameChanges> targetGameChanges,
        GameChanges currentGameChanges,
        GameState initialGameState
    )
    {
        var evaluatorFn = this.GetById(evaluatorId);
        var evaluator = evaluatorFn(evaluatorInput);
        var score = await evaluator.Evaluate(targetGameChanges, currentGameChanges, initialGameState);

        return score;
    }
}
