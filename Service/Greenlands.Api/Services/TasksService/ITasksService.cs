namespace Greenlands.Api.Services;

public interface ITasksService
{
    public Task<IList<GreenlandsTask>> Get(string tournamentId, TaskQueryOptions? options = null);

    public Task<GreenlandsTask?> Find(string tournamentId, string taskId, TaskQueryOptions? options = null);

    public Task<GreenlandsTask> Add(string tournamentId, TaskInput taskInput);

    public Task<GreenlandsTask> Update(GreenlandsTask task);

    public Task<GreenlandsTask> Delete(string tournamentId, string taskId);

    public Task<GameState> GetInitialGameState(GreenlandsTask task);

    public Task<IList<GameChanges>> GetTargetGameChanges(GreenlandsTask task);
}
