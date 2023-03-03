namespace PlaiGround.Api.Services;

public interface IGamesService
{
    public Task<IList<Game>> Get(string taskId);

    public Task<Game?> Find(string taskId, string gameId);

    public Task<Game> Create(string tournamentId, string taskId);

    public Task<Game> Update(string gameId, string taskId, GameUpdate gameUpdate);

    public Task<Game> Delete(string taskId, string gameId);

    public Task<IList<BaseEvent>> GetEvents(string gameId);

    public Task<Uri> Save(string taskId, string gameId);

    public Task<double> Evaluate(string tournamentId, string taskId, string gameId);
}
