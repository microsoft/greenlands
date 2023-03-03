namespace PlaiGround.Api.Services;

public interface IAgentsService
{
    public Task<IList<AgentService>> Get(string? agentChallengeId);

    public Task<AgentService?> Find(string agentServiceId);

    public Task<AgentConnectionInfo> GetConnectionInfo(string agentServiceId);

    public Task<AgentService> Add(string userId, AgentServiceInput agentServiceInput);

    public Task Delete(string agentServiceId);
}
