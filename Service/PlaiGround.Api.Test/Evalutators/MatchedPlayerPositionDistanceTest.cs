using NUnit.Framework;
using PlaiGround.Api.Evaluators;
using PlaiGround.Api.Events.v1.Helpers;

namespace PlaiGround.Api.Test.Evaluators;

public class MatchedPlayerPositionDistanceTests
{
    [SetUp]
    public void Setup()
    {
    }

    [Test]
    public void GivenTwoIdenticalLocationsDistanceShouldBe0()
    {
        // Arrange
        var l1 = new Location(0, 0, 0, 0, 0);
        var l2 = new Location(0, 0, 0, 0, 0);
        var expectedDistance = 0;

        // Act
        var distance = MatchedPlayerLocationEvaluator.GetDistance(l1, l2);

        // Assert
        Assert.AreEqual(expectedDistance, distance);
    }

    [Test]
    public void GivenTwoLocations1DistanceApartShouldBe1()
    {
        // Arrange
        var l1 = new Location(0, 0, 0, 0, 0);
        var l2 = new Location(0, 0, 1, 0, 0);
        var expectedDistance = 1;

        // Act
        var distance = MatchedPlayerLocationEvaluator.GetDistance(l1, l2);

        // Assert
        Assert.AreEqual(expectedDistance, distance);
    }
}
