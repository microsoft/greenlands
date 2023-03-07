using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Greenlands.Api.Auth;
using Greenlands.Api.Services;

namespace Greenlands.Api.Controllers;

/// <summary>
/// Collection of endpoints related to functionality for Tasks.
/// </summary>
[ApiVersion("1.0")]
[Route("api/v{v:apiVersion}/tournaments/{tournamentId}/[controller]")]
[Produces("application/json")]
[ApiController]
public class TasksController : ControllerBase
{
    private readonly ILogger<TasksController> _logger;
    private readonly ITasksService _tasksService;
    private readonly IHumanChallengesService _humanChallengesService;
    private readonly IOwningTeamMemberAuthorizationService _owningTeamMemberAuthorizationService;

    public TasksController(
        ILogger<TasksController> logger,
        ITasksService tasksService,
        IHumanChallengesService humanChallengesService,
        IOwningTeamMemberAuthorizationService owningTeamMemberAuthorizationService
    )
    {
        _logger = logger;
        _tasksService = tasksService;
        _humanChallengesService = humanChallengesService;
        _owningTeamMemberAuthorizationService = owningTeamMemberAuthorizationService;
    }

    /// <summary>
    /// Returns a list of all the tasks that we know of for the specified
    /// tournament. Optionally, we can also filter by agent challenge ID and/or
    /// task IDs.
    /// </summary>
    [HttpGet(Name = "GetTasks")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(IList<GreenlandsTask>), 200)]
    public async Task<ActionResult> Get(string tournamentId, [FromQuery] string? agentChallengeId, [FromQuery] List<string>? taskIds)
    {
        var queryOptions = new TaskQueryOptions
        {
            AgentChallengeId = agentChallengeId,
            TaskIds = taskIds?.ToHashSet()
        };

        var tasks = await _tasksService.Get(tournamentId, queryOptions);

        return Ok(tasks);
    }

    /// <summary>
    /// Returns the task with the given ID for the specified tournament, if
    /// there is one.
    /// </summary>
    [HttpGet("{taskId}", Name = "GetTaskById")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(GreenlandsTask), 200)]
    public async Task<ActionResult> GetById(string tournamentId, string taskId)
    {
        var task = await _tasksService.Find(tournamentId, taskId);
        if (task == null)
        {
            return NotFound($"Could not find task with id {taskId}");
        }

        return Ok(task);
    }

    /// <summary>
    /// Returns the task with the given ID for the specified tournament, returns
    /// the full task data instead of a summary.
    [HttpGet("{taskId}/complete", Name = "GetCompleteTaskById")]
    [ProducesResponseType(404)]
    [ProducesResponseType(typeof(CompleteTask), 200)]
    public async Task<ActionResult> GetCompleteById(string tournamentId, string taskId)
    {
        var task = await _tasksService.Find(tournamentId, taskId, new TaskQueryOptions { LoadBlobData = true });
        if (task == null)
        {
            return NotFound($"Could not find task with id {taskId}");
        }

        return Ok(task);
    }

    /// <summary>
    /// Creates a new task for the specified tournament.
    /// </summary>
    [HttpPost(Name = "CreateTask")]
    [ProducesResponseType(typeof(ClientErrorResponse), 400)]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(GreenlandsTask), 201)]
    public async Task<ActionResult> Create(string tournamentId, [FromBody] TaskInput taskInput)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        // Form validation

        if (TaskInput.IsTaskExpectedCompletionDurationOverMaximum(taskInput.ExpectedCompletionDurationSeconds))
        {
            var clientErrorResponse = new ClientErrorResponse
            {
                Errors = new Dictionary<string, List<string>>
                {
                    [TaskInput.ExpectedCompletionDurationErrorName] = new List<string>
                    {
                        TaskInput.GetExpectedCompletionDurationErrorText(taskInput.ExpectedCompletionDurationSeconds)
                    }
                }
            };

            return BadRequest(clientErrorResponse);
        }

        var task = await _tasksService.Add(tournamentId, taskInput);

        var routeValues = new
        {
            tournamentId = tournamentId,
            taskId = task.Id
        };

        return CreatedAtAction(nameof(GetById), routeValues, task);
    }

    /// <summary>
    /// Updates the task with the given ID for the specified tournament.
    /// </summary>
    [HttpPut("{taskId}", Name = "UpdateTask")]
    [ProducesResponseType(typeof(ClientErrorResponse), 400)]
    [ProducesResponseType(401)]
    [ProducesResponseType(typeof(GreenlandsTask), 200)]
    public async Task<ActionResult> Update(
        string tournamentId,
        string taskId,
        [FromBody] GreenlandsTask task
    )
    {
        if (task == null)
        {
            return BadRequest($"You attempted to update task: {taskId} but task was not provided");
        }

        if (!string.Equals(taskId, task.Id, StringComparison.OrdinalIgnoreCase))
        {
            return BadRequest($"You attempted to update task: {taskId} but the id {task.Id} of the task provided did not match.");
        }

        if (!string.Equals(tournamentId, task.TournamentId, StringComparison.OrdinalIgnoreCase))
        {
            return BadRequest($"You attempted to update task: {taskId} but the tournament id in the route {tournamentId} did not match the tournament id of the task: {task.TournamentId}.");
        }

        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        if (TaskInput.IsTaskExpectedCompletionDurationOverMaximum(task.ExpectedCompletionDurationSeconds))
        {
            var clientErrorResponse = new ClientErrorResponse
            {
                Errors = new Dictionary<string, List<string>>
                {
                    [TaskInput.ExpectedCompletionDurationErrorName] = new List<string>
                    {
                        TaskInput.GetExpectedCompletionDurationErrorText(task.ExpectedCompletionDurationSeconds)
                    }
                }
            };

            return BadRequest(clientErrorResponse);
        }


        var updatedTask = await _tasksService.Update(task);

        return Ok(updatedTask);
    }

    /// <summary>
    /// Deletes the task with the given ID for the specified tournament.
    /// </summary>
    [HttpDelete("{taskId}", Name = "DeleteTask")]
    [ProducesResponseType(400)]
    [ProducesResponseType(401)]
    [ProducesResponseType(204)]
    public async Task<ActionResult> Delete(string tournamentId, string taskId)
    {
        var invalidOperationException = await _owningTeamMemberAuthorizationService.IsUserMemberOfTeamOwningTournament(User, tournamentId);
        if (invalidOperationException != null)
        {
            return Unauthorized(invalidOperationException.Message);
        }

        var humanChallengesForTournament = await _humanChallengesService.GetManyByTournamentId(tournamentId);

        var humanChallengesReferringToTask = humanChallengesForTournament.Where(hc => hc.TaskIds.Contains(taskId));
        if (humanChallengesReferringToTask.Any())
        {
            return BadRequest($"You attempted to delete task with id: {taskId} but that task was still being used by human challenges: {string.Join(", ", humanChallengesReferringToTask.Select(hc => hc.Id))} You must delete the challenges first.");
        }

        await _tasksService.Delete(tournamentId, taskId);

        return NoContent();
    }

}
