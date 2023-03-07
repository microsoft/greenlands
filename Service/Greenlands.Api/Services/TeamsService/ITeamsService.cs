namespace Greenlands.Api.Services;

public interface ITeamsService
{
    public Task<IList<Team>> GetAll();

    public Task<IList<Team>> GetMyTeams(string userId);

    public Task<Team> Join(string teamId, string userId);

    public Task<Team> Leave(string teamId, string userId);

    public Task<Team?> Find(string teamId);

    public Task<Team> Create(TeamInput teamInput, string userId);

    public Task Delete(string teamId);
}
