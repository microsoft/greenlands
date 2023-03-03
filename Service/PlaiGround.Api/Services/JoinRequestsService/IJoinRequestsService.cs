namespace PlaiGround.Api.Services;

public interface IJoinRequestsService
{
    public Task<IList<JoinRequest>> Get(string teamId);

    public Task<JoinRequest?> Find(string teamId, string joinRequestId);

    public Task<JoinRequest> CreateRequest(JoinRequestInput joinRequestInput);

    public Task Approve(string teamId, string joinRequestId);

    public Task Reject(string teamId, string joinRequestId);
}
