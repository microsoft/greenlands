namespace PlaiGround.Api.Services;

public interface IEventsService
{
    public Task<IList<BaseEvent>> GetEventsFromCosmos(string tournamentId, string groupId, IList<string>? taskIds = null, DateTime? afterDateTime = null);

    public Task<IList<BaseEvent>> GetEventsFromCosmos(string gameId);

    public Task<IList<BaseEvent>> GetEventsFromEventHub(string gameId);
}
