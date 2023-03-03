using PlaiGround.Api.Events.v1.Helpers;

namespace PlaiGround.Api.Evaluators;

/// <summary>
/// Evaluation score is percentage of roles that are considered within range of target position.
/// Each individual role is only 0% or 100% if player is within radius of target locatio
/// 
/// Example: Role1 (100%) + Role (0%) / 2 = 0.5
/// </summary>
public class MatchedPlayerLocationEvaluator : Evaluator
{
    private readonly double _allowedLocationOffsetDistance = 0;

    public MatchedPlayerLocationEvaluator(int allowedLocationOffsetDistance)
    {
        // TODO: How do we store or allow setting this radius during task editing?
        // Perhaps it is a setting we can save on the task object separate from GameChanges
        _allowedLocationOffsetDistance = allowedLocationOffsetDistance;
    }

    public override Task<double> EvaluateWithoutBoundaryCheck(
        IList<GameChanges> targetGameChangesList,
        GameChanges currentGameChanges,
        GameState initialGameState
    )
    {
        // TODO: Determine how to evaluate currentChanges against each intermediate GameChange in list
        var targetGameChanges = targetGameChangesList.Last();
        var roleToMatchedLocation = new Dictionary<string, bool>();

        // For each role with expected position check if the current role is close to position
        foreach (var (roleId, playerChanges) in (targetGameChanges.PlayerChanges ?? new Dictionary<string, PlayerChanges>()))
        {
            var expectedRoleLocation = playerChanges?.Location;
            if (expectedRoleLocation != null)
            {
                var currentRoleLocation = currentGameChanges.PlayerChanges?.GetValueOrDefault(roleId)?.Location;

                bool isWithinAllowedDistance;
                // If role doesn't have a position defined this is likely a bug, but assume it is not in expected position
                if (currentRoleLocation == null)
                {
                    isWithinAllowedDistance = false;
                }
                else
                {
                    var distanceFromExpectedLocation = GetDistance(expectedRoleLocation, currentRoleLocation);
                    isWithinAllowedDistance = distanceFromExpectedLocation <= _allowedLocationOffsetDistance;
                }

                roleToMatchedLocation[roleId] = isWithinAllowedDistance;
            }
        }

        if (roleToMatchedLocation.Count == 0)
        {
            throw new InvalidOperationException($"You attempted to evaluate a game using Matched Location on a task that has no expected positions. Please update the Task and retry the game.");
        }

        var numberOfRolesAtLocation = roleToMatchedLocation.Values
            .Aggregate(0, (aggregate, inLocation) =>
            {
                return inLocation
                    ? aggregate + 1
                    : aggregate;
            });

        var score = (double)numberOfRolesAtLocation / roleToMatchedLocation.Count;

        return Task.FromResult(score);
    }

    /// <summary>
    /// Given two locations, return the distance between the locations
    /// </summary>
    public static double GetDistance(Location l1, Location l2)
    {
        var differences = new List<double>
        {
            l1.X - l2.X,
            l1.Y - l2.Y,
            l1.Z - l2.Z,
        };

        var squaredDifferences = differences.Select(d => Math.Pow(d, 2));
        var distance = Math.Sqrt(squaredDifferences.Sum());

        return distance;
    }
}
