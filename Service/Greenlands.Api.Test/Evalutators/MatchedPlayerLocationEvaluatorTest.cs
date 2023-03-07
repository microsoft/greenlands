using NUnit.Framework;
using Greenlands.Api.Evaluators;
using Greenlands.Api.Events.v1.Helpers;
using Greenlands.Api.Models;

namespace Greenlands.Api.Test.Evaluators;

public class MatchedPlayerLocationEvalutorTests
{
    [SetUp]
    public void Setup()
    {
    }

    [Test]
    public async Task MatchedPlayerLocationEvaluatorWithinDistanceShouldReturn1()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges>
        {
            new GameChanges
            {
                PlayerChanges = new Dictionary<string, PlayerChanges>
                {
                    ["role1"] = new PlayerChanges
                    {
                        Location = new Location(1, 2, 4, 0, 0),
                    }
                }
            }
        };

        var currentNameChangs = new GameChanges
        {
            PlayerChanges = new Dictionary<string, PlayerChanges>
            {
                ["role1"] = new PlayerChanges
                {
                    Location = new Location(2, 3, 6, 0, 0),
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 1.0;
        var matchedPlayerLocationEvaluator = new MatchedPlayerLocationEvaluator(3);

        // Act
        var score = await matchedPlayerLocationEvaluator.Evaluate(targetGameChanges, currentNameChangs, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public async Task MatchedPlayerLocationEvaluatorWithNoLocationShouldReturn0()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges>
        {
            new GameChanges
            {
                PlayerChanges = new Dictionary<string, PlayerChanges>
                {
                    ["role1"] = new PlayerChanges
                    {
                        Location = new Location(1, 2, 4, 0, 0),
                    }
                }
            }
        };

        var currentNameChangs = new GameChanges();
        var initialGameState = new GameState();

        var expectedScore = 0.0;
        var matchedPlayerLocationEvaluator = new MatchedPlayerLocationEvaluator(1);

        // Act
        var score = await matchedPlayerLocationEvaluator.Evaluate(targetGameChanges, currentNameChangs, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public async Task MatchedPlayerLocationEvaluatorWithLocationButBeyondDistanceLimitShouldReturn0()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges>
        {
            new GameChanges
            {
                PlayerChanges = new Dictionary<string, PlayerChanges>
                {
                    ["role1"] = new PlayerChanges
                    {
                        Location = new Location(1, 2, 4, 0, 0),
                    }
                }
            }
        };

        var currentNameChangs = new GameChanges
        {
            PlayerChanges = new Dictionary<string, PlayerChanges>
            {
                ["role1"] = new PlayerChanges
                {
                    Location = new Location(2, 13, 6, 0, 0),
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 0.0;
        var matchedPlayerLocationEvaluator = new MatchedPlayerLocationEvaluator(3);

        // Act
        var score = await matchedPlayerLocationEvaluator.Evaluate(targetGameChanges, currentNameChangs, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }


    [Test]
    public async Task MatchedPlayerLocationEvaluatorWithOneRoleInLocationAndOtherNotShouldReturn0()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges>
        {
            new GameChanges
            {
                PlayerChanges = new Dictionary<string, PlayerChanges>
                {
                    ["role1"] = new PlayerChanges
                    {
                        Location = new Location(1, 2, 4, 0, 0),
                    },
                    ["role2"] = new PlayerChanges
                    {
                        Location = new Location(1, 2, 4, 0, 0),
                    }
                }
            }
        };

        var currentNameChangs = new GameChanges
        {
            PlayerChanges = new Dictionary<string, PlayerChanges>
            {
                ["role1"] = new PlayerChanges
                {
                    Location = new Location(1, 2, 4, 0, 0),
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 0.5;
        var matchedPlayerLocationEvaluator = new MatchedPlayerLocationEvaluator(3);

        // Act
        var score = await matchedPlayerLocationEvaluator.Evaluate(targetGameChanges, currentNameChangs, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public void MatchedPlayerLocationEvaluatorWithNoExpectedPlayerLocationsThrows()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges>
        {
            new GameChanges()
        };

        var currentNameChanges = new GameChanges();
        var initialGameState = new GameState();

        var matchedPlayerLocationEvaluator = new MatchedPlayerLocationEvaluator(3);

        // Act + Assert
        Assert.ThrowsAsync<InvalidOperationException>(async () =>
        {
            await matchedPlayerLocationEvaluator.Evaluate(targetGameChanges, currentNameChanges, initialGameState);
        });
    }
}
