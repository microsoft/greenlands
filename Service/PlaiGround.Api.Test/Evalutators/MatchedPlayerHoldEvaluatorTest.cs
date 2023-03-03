using NUnit.Framework;
using PlaiGround.Api.Evaluators;
using PlaiGround.Api.Models;

namespace PlaiGround.Api.Test.Evaluators;

public class MatchedPlayerHoldEvalutorTests
{
    [SetUp]
    public void Setup()
    {
    }

    [Test]
    public async Task MatchedPlayerHoldEvaluatorWithCorrectItemShouldReturn1()
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
                        CurrentItem = new Block(1)
                    }
                }
            }
        };

        var currentNameChanges = new GameChanges
        {
            PlayerChanges = new Dictionary<string, PlayerChanges>
            {
                ["role1"] = new PlayerChanges
                {
                    CurrentItem = new Block(1)
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 1.0;
        var matchedPlayerHoldEvaluator = new MatchedPlayerHoldEvaluator();

        // Act
        var score = await matchedPlayerHoldEvaluator.Evaluate(targetGameChanges, currentNameChanges, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public async Task MatchedPlayerHoldEvaluatorWithNoCurrentItemsShouldReturn0()
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
                        CurrentItem = new Block(1)
                    }
                }
            }
        };

        var currentNameChanges = new GameChanges();
        var initialGameState = new GameState();

        var expectedScore = 0.0;
        var matchedPlayerHoldEvaluator = new MatchedPlayerHoldEvaluator();

        // Act
        var score = await matchedPlayerHoldEvaluator.Evaluate(targetGameChanges, currentNameChanges, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public async Task MatchedPlayerHoldEvaluatorWithWrongHoldItemShouldReturn0()
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
                        CurrentItem = new Block(1)
                    }
                }
            }
        };

        var currentNameChanges = new GameChanges
        {
            PlayerChanges = new Dictionary<string, PlayerChanges>
            {
                ["role1"] = new PlayerChanges
                {
                    CurrentItem = new Block(2)
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 0.0;
        var matchedPlayerHoldEvaluator = new MatchedPlayerHoldEvaluator();

        // Act
        var score = await matchedPlayerHoldEvaluator.Evaluate(targetGameChanges, currentNameChanges, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public async Task MatchedPlayerHoldEvaluatorWithOneRoleHavingCorrectButOtherNotShouldReturnHalf()
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
                        CurrentItem = new Block(1)
                    },
                    ["role2"] = new PlayerChanges
                    {
                        CurrentItem = new Block(2)
                    }
                }
            }
        };

        var currentNameChanges = new GameChanges
        {
            PlayerChanges = new Dictionary<string, PlayerChanges>
            {
                ["role1"] = new PlayerChanges
                {
                    CurrentItem = new Block(1)
                },
                ["role2"] = new PlayerChanges
                {
                    // Note this is different than expected
                    CurrentItem = new Block(3)
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 0.5;
        var matchedPlayerHoldEvaluator = new MatchedPlayerHoldEvaluator();

        // Act
        var score = await matchedPlayerHoldEvaluator.Evaluate(targetGameChanges, currentNameChanges, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public void MatchedPlayerHoldEvaluatorWithNoExpectedPlayerHoldsChangesThrows()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges>
        {
            new GameChanges()
        };

        var currentNameChanges = new GameChanges();
        var initialGameState = new GameState();

        var matchedPlayerHoldEvaluator = new MatchedPlayerHoldEvaluator();

        // Act + Assert
        Assert.ThrowsAsync<InvalidOperationException>(async () =>
        {
            await matchedPlayerHoldEvaluator.Evaluate(targetGameChanges, currentNameChanges, initialGameState);
        });
    }
}
