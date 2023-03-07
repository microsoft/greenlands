namespace Greenlands.Api.Services;

public interface IEvaluationService
{
    public object GetEvaluationInput(EvaluatorIds evaluatorId, Tournament tournament, GreenlandsTask task);

    public Task<double> Evaluate(EvaluatorIds evaluatorId, object evaluatorInput, IList<GameChanges> targetGameChanges, GameChanges currentGameChanges, GameState initialGameState);
}
