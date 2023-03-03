using NUnit.Framework;
using PlaiGround.Api.Evaluators;
using PlaiGround.Api.Events.v1.Helpers;
using PlaiGround.Api.Models;

namespace PlaiGround.Api.Test.Evaluators;

public class MatchedPlayerIventoryEvalutorTests
{
    [SetUp]
    public void Setup()
    {
    }

    [Test]
    public async Task MatchedPlayerInventoryEvaluatorWithFullMatchShouldReturn1()
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
                        InventoryChanges = new Dictionary<int, int>
                        {
                            [1] = 1,
                            [2] = 2,
                            [3] = 3,
                        }
                    },
                    ["role2"] = new PlayerChanges
                    {
                        InventoryChanges = new Dictionary<int, int>
                        {
                            [4] = 4,
                            [5] = 5,
                            [6] = 6,
                        }
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
                    InventoryChanges = new Dictionary<int, int>
                    {
                        [1] = 1,
                        [2] = 2,
                        [3] = 3,
                    }
                },
                ["role2"] = new PlayerChanges
                {
                    InventoryChanges = new Dictionary<int, int>
                    {
                        [4] = 4,
                        [5] = 5,
                        [6] = 6,
                    }
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 1.0;
        var matchedPlayerInventoryEvaluator = new MatchedPlayerInventoryEvaluator();

        // Act
        var score = await matchedPlayerInventoryEvaluator.Evaluate(targetGameChanges, currentNameChanges, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public async Task MatchedPlayerInventoryEvaluatorWithNoMatchShouldReturn0()
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
                        InventoryChanges = new Dictionary<int, int>
                        {
                            [1] = 1,
                            [2] = 2,
                            [3] = 3,
                        }
                    }
                }
            }
        };

        var currentNameChanges = new GameChanges
        {
            WorldChanges = new WorldChanges
            {
                BlockChanges = new Dictionary<LocationString, Block>
                {
                    [new Location(1, 2, 0).ToString()] = new Block(1),
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = 0.0;
        var matchedPlayerInventoryEvaluator = new MatchedPlayerInventoryEvaluator();

        // Act
        var score = await matchedPlayerInventoryEvaluator.Evaluate(targetGameChanges, currentNameChanges, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public async Task MatchedPlayerInventoryEvaluatorWithPartialMatchShouldReturnPositiveFraction()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges>
        {
            new GameChanges
            {
                PlayerChanges = new Dictionary<RoleId, PlayerChanges>
                {
                    ["role1"] = new PlayerChanges
                    {
                        InventoryChanges = new Dictionary<ItemType, ItemQuantity>
                        {
                            [1] = 1,
                            [2] = 2,
                            [3] = 3,
                        }
                    },
                    ["role2"] = new PlayerChanges
                    {
                        InventoryChanges = new Dictionary<ItemType, ItemQuantity>
                        {
                            [4] = 4,
                            [5] = 5,
                        }
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
                    InventoryChanges = new Dictionary<ItemType, ItemQuantity>
                    {
                        [1] = 1,
                        // User has more items than expected, causes 0% match
                        [2] = 10,
                        [3] = 1,
                    }
                },
                ["role2"] = new PlayerChanges
                {
                    InventoryChanges = new Dictionary<ItemType, ItemQuantity>
                    {
                        [4] = 1
                    }
                }
            }
        };

        var initialGameState = new GameState();

        var expectedScore = (double)0.316;
        var matchedPlayerInventoryEvaluator = new MatchedPlayerInventoryEvaluator();

        // Act
        var score = await matchedPlayerInventoryEvaluator.Evaluate(targetGameChanges, currentNameChanges, initialGameState);

        // Assert
        Assert.AreEqual(expectedScore, score, 0.001);
    }

    [Test]
    public void MatchedPlayerInventoryEvaluatorWithNoExpectedInventoryChangesThrows()
    {
        // Arrange
        var targetGameChanges = new List<GameChanges>
        {
            new GameChanges()
        };

        var currentNameChanges = new GameChanges();
        var initialGameState = new GameState();

        var matchedPlayerInventoryEvaluator = new MatchedPlayerInventoryEvaluator();

        // Act + Assert
        Assert.ThrowsAsync<InvalidOperationException>(async () =>
        {
            await matchedPlayerInventoryEvaluator.Evaluate(targetGameChanges, currentNameChanges, initialGameState);
        });
    }

    [Test]
    public void GivenFewerThanExpectedInventoryReturnCurrentQuantityDividedByExpected()
    {
        // Arrange
        var currentQuantityOfMaterialType = 4;
        var expectedQuantityOfMaterialType = 10;
        var expectedScore = (double)currentQuantityOfMaterialType / expectedQuantityOfMaterialType;

        // Act
        var score = MatchedPlayerInventoryEvaluator.GetMatchPercentage(currentQuantityOfMaterialType, expectedQuantityOfMaterialType);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public void GivenTwoTimesMoreThanExpectedInventoryReturn0()
    {
        // Arrange
        var currentQuantityOfMaterialType = 5;
        var expectedQuantityOfMaterialType = 2;
        var expectedScore = (double)0;

        // Act
        var score = MatchedPlayerInventoryEvaluator.GetMatchPercentage(currentQuantityOfMaterialType, expectedQuantityOfMaterialType);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }

    [Test]
    public void GivenLessThanTwoTimesButMoreThanExpectedInventoryReturnExcessQuantityDividedByExpected()
    {
        // Arrange
        var currentQuantityOfMaterialType = 6;
        var expectedQuantityOfMaterialType = 5;
        var expectedScore = (double)4 / 5;

        // Act
        var score = MatchedPlayerInventoryEvaluator.GetMatchPercentage(currentQuantityOfMaterialType, expectedQuantityOfMaterialType);

        // Assert
        Assert.AreEqual(expectedScore, score);
    }
}
