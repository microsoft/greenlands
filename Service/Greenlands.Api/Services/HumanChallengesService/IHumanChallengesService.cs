namespace Greenlands.Api.Services;

public interface IHumanChallengesService
{
    public Task<IList<HumanChallenge>> GetAll(HumanChallengeQueryOptions? humanChallengeOptions);

    public Task<IList<HumanChallenge>> GetManyByTournamentId(string tournamentId);

    public Task<IList<HumanChallenge>> FindManyByIds(ISet<string> humanChallengeIds);

    public Task<HumanChallenge?> FindById(string tournamentId, string humanChallengeId);

    public Task<HumanChallenge> Add(HumanChallengeInput humanChallengeInput);

    public Task<HumanChallenge> End(string tournamentId, string humanChallengeId);

    public Task<HumanChallengeData> GetHumanChallengeData(HumanChallenge humanChallenge);

    public Task<HumanChallenge> UpdateDataCollectionInfo(string tournamentId, string humanChallengeId);

    public Task Delete(string tournamentId, string humanChallengeId);
}
