using Greenlands.Api.Evaluators;
using Greenlands.Api.Events.v1.Helpers;
using Greenlands.Api.Models;
using NUnit.Framework;

namespace Greenlands.Api.Test.Evaluators;

public class MatchedWorldBlocksEvalutorTests
{
    [SetUp]
    public void Setup()
    {
    }

    [Test]
    public async Task MatchedWorldBlocksEvaluatorWithFullMatchShouldReturn1()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges>
            {
                new GameChanges
                {
                    WorldChanges = new WorldChanges
                    {
                        BlockChanges = new Dictionary<LocationString, Block>
                        {
                            [new Location(1,1,0).ToString()] = new Block(1)
                        }
                    }
                }
            };

        var currentNameChangs = new GameChanges
        {
            WorldChanges = new WorldChanges
            {
                BlockChanges = new Dictionary<LocationString, Block>
                {
                    [new Location(1, 1, 0).ToString()] = new Block(1)
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 1.0;
        var matchedWorldBlocksEvaluator = new MatchedWorldBlocksEvaluator();

        // Act
        var score = await matchedWorldBlocksEvaluator.Evaluate(targetGameChanges, currentNameChangs, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public async Task MatchedWorldBLocksEvaluatorWithNoMatchShouldReturn0()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges> {
                new GameChanges
                {
                    WorldChanges = new WorldChanges
                    {
                        BlockChanges = new Dictionary<LocationString, Block>
                        {
                            [new Location(1,1,0).ToString()] = new Block(1)
                        }
                    }
                }
            };

        var currentNameChangs = new GameChanges
        {
            WorldChanges = new WorldChanges
            {
                BlockChanges = new Dictionary<LocationString, Block>
                {
                    [new Location(1, 2, 0).ToString()] = new Block(1)
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 0.0;
        var matchedWorldBlocksEvaluator = new MatchedWorldBlocksEvaluator();

        // Act
        var score = await matchedWorldBlocksEvaluator.Evaluate(targetGameChanges, currentNameChangs, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public async Task MatchedWorldBLocksEvaluatorWithPartialMatchShouldReturnPositiveFraction()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges> {
                new GameChanges
                {
                    WorldChanges = new WorldChanges
                    {
                        BlockChanges = new Dictionary<LocationString, Block>
                        {
                            [new Location(1,1,0).ToString()] = new Block(1),
                            [new Location(1,2,0).ToString()] = new Block(1),
                        }
                    }
                }
            };

        var currentNameChangs = new GameChanges
        {
            WorldChanges = new WorldChanges
            {
                BlockChanges = new Dictionary<LocationString, Block>
                {
                    [new Location(1, 1, 0).ToString()] = new Block(1),
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 0.5;
        var matchedWorldBlocksEvaluator = new MatchedWorldBlocksEvaluator();

        // Act
        var score = await matchedWorldBlocksEvaluator.Evaluate(targetGameChanges, currentNameChangs, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public void MatchedWorldBlocksEvaluatorWithNoExpectedWorldBlocksThrows()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges>
        {
            new GameChanges()
        };

        var currentGameChanges = new GameChanges();
        var initialGameState = new GameState();

        var matchedWorldBlocksEvaluator = new MatchedWorldBlocksEvaluator();

        // Act + Assert
        Assert.ThrowsAsync<InvalidOperationException>(async () =>
        {
            await matchedWorldBlocksEvaluator.Evaluate(targetGameChanges, currentGameChanges, initialGameState);
        });
    }

    [Test]
    public void GivenLessChangesThanExpectedReturnMatchesDividedByExpected()
    {
        // Arrange
        var currentBlockChangeMatches = new Dictionary<LocationString, BlockChangeMatch>
        {
            [new Location(0, 0, 0).ToString()] = new BlockChangeMatch(
                block: new Block(1),
                isMatched: true
            )
        };

        var targetBlockChanges = new Dictionary<LocationString, Block>
        {
            [new Location(0, 0, 0).ToString()] = new Block(1),
            [new Location(0, 0, 1).ToString()] = new Block(2),
        };

        var matchedBlocksCount = currentBlockChangeMatches.Values.Where(bc => bc.IsMatched).Count();
        var expectedScore = (double)matchedBlocksCount / targetBlockChanges.Count;

        // Act
        var score = MatchedWorldBlocksEvaluator.GetPercentageMatchedBlocks(currentBlockChangeMatches.Values.ToList(), targetBlockChanges.Count);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public void GivenMoreChangesThanExpectedReturnMatchesDividedByChanges()
    {
        // Arrange
        var currentBlockChangeMatches = new Dictionary<LocationString, BlockChangeMatch>
        {
            [new Location(0, 0, 0).ToString()] = new BlockChangeMatch(
                block: new Block(1),
                isMatched: true
            ),
            [new Location(0, 0, 1).ToString()] = new BlockChangeMatch(
                block: new Block(1),
                isMatched: false
            )
        };

        var targetBlockChanges = new Dictionary<LocationString, Block>
        {
            [new Location(0, 0, 0).ToString()] = new Block(1),
        };

        var matchedBlocksCount = currentBlockChangeMatches.Values.Where(bc => bc.IsMatched).Count();
        var expectedScore = (double)matchedBlocksCount / currentBlockChangeMatches.Count;

        // Act
        var score = MatchedWorldBlocksEvaluator.GetPercentageMatchedBlocks(currentBlockChangeMatches.Values.ToList(), targetBlockChanges.Count);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }
}
