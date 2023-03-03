namespace PlaiGround.Api.Services;

public interface IAgentChallengesService
{
    public Task<IList<AgentChallenge>> GetAll();

    public Task<IList<AgentChallenge>> GetManyByTournamentId(string tournamentId);

    public Task<AgentChallenge?> FindById(string tournamentId, string agentChallengeId);

    public Task<AgentChallenge> Add(AgentChallengeInput agentChallengeInput);

    public Task<AgentChallenge> End(string tournamentId, string agentChallengeId);

    public Task<AgentChallenge> UpdateDataCollectionInfo(string tournamentId, string agentChallengeId);

    public Task Delete(string tournamentId, string agentChallengeId);
}
