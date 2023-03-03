namespace PlaiGround.Api.Services;

public interface IEvaluationService
{
    public object GetEvaluationInput(EvaluatorIds evaluatorId, Tournament tournament, PlaiGroundTask task);

    public Task<double> Evaluate(EvaluatorIds evaluatorId, object evaluatorInput, IList<GameChanges> targetGameChanges, GameChanges currentGameChanges, GameState initialGameState);
}
