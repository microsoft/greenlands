namespace Greenlands.Api.Models;

public interface IEvaluator
{
    public Task<double> Evaluate(IList<GameChanges> targetGameState, GameChanges currentGameChanges, GameState initialGameState);
}
