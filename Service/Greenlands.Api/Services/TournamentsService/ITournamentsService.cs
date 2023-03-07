namespace Greenlands.Api.Services;

public interface ITournamentsService
{
    public Task<IList<Tournament>> GetAll();

    public Task<IList<string>> GetTournamentIdsOwnedByTeamIds(IList<string> teamIds);

    public Task<Tournament?> Find(string tournamentId);

    public Task<Tournament> Create(TournamentInput tournamentInput);

    public Task<Tournament> Update(string tournamentId, TournamentUpdate tournament);

    public Task<Tournament> Remove(string tournamentId);
}
