namespace PlaiGround.Api.Services;

public interface ITasksService
{
    public Task<IList<PlaiGroundTask>> Get(string tournamentId, TaskQueryOptions? options = null);

    public Task<PlaiGroundTask?> Find(string tournamentId, string taskId, TaskQueryOptions? options = null);

    public Task<PlaiGroundTask> Add(string tournamentId, TaskInput taskInput);

    public Task<PlaiGroundTask> Update(PlaiGroundTask task);

    public Task<PlaiGroundTask> Delete(string tournamentId, string taskId);

    public Task<GameState> GetInitialGameState(PlaiGroundTask task);

    public Task<IList<GameChanges>> GetTargetGameChanges(PlaiGroundTask task);
}
