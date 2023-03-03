package com.microsoft.plaiground.common.entities;

import com.microsoft.plaiground.client.model.AreaCube;
import com.microsoft.plaiground.client.model.Block;
import com.microsoft.plaiground.common.utils.LocationUtils;
import java.util.Map;
import java.util.Optional;
import org.bukkit.util.Vector;

/**
 * This class is meant to be used as a way to summarize structures inside MineCraft (e.g. target
 * structures). It basically represents a cube in space, with a center and sizes
 */
public class GeometryInfo {

  public Vector size;
  public Vector center;

  public GeometryInfo(Vector size, Vector center) {
    this.size = size;
    this.center = center;
  }

  /**
   * Given the map of {@link com.microsoft.plaiground.client.model.Location} string
   * [x,y,z,pitch,yaw], {@link com.microsoft.plaiground.client.model.Block} that represents the
   * target structure, return an instance of {@link GeometryInfo} that represents it.
   *
   * If the provided target block changes are null or empty then no {@link GeometryInfo} can be
   * extracted (since there really is no target structure), so this method returns an
   * {@link Optional#empty()}.
   */
  public static Optional<GeometryInfo> fromTargetStructure(Map<String, Block> targetBlockChanges) {
    if (targetBlockChanges == null || targetBlockChanges.isEmpty()) {
      return Optional.empty();
    }

    var westMostLocation = Float.POSITIVE_INFINITY;
    var eastMostLocation = Float.NEGATIVE_INFINITY;

    var southMostLocation = Float.NEGATIVE_INFINITY;
    var northMostLocation = Float.POSITIVE_INFINITY;

    var highestBlock = Float.NEGATIVE_INFINITY;
    var lowestBlock = Float.POSITIVE_INFINITY;

    for (var entry : targetBlockChanges.entrySet()) {
      var locationString = entry.getKey();
      var location = LocationUtils.fromStringToPlaigroundLocation(locationString);

      // X dim size
      if (location.getX() < westMostLocation) {
        westMostLocation = location.getX();
      }

      if (location.getX() > eastMostLocation) {
        eastMostLocation = location.getX();
      }

      // Z dim size
      if (location.getZ() > southMostLocation) {
        southMostLocation = location.getZ();
      }

      if (location.getZ() < northMostLocation) {
        northMostLocation = location.getZ();
      }

      // Y dim size
      if (location.getY() > highestBlock) {
        highestBlock = location.getY();
      }

      if (location.getY() < lowestBlock) {
        lowestBlock = location.getY();
      }
    }

    var width = Math.abs(eastMostLocation - westMostLocation);
    var depth = Math.abs(southMostLocation - northMostLocation);
    var height = Math.abs(highestBlock - lowestBlock);

    var size = new Vector(width, height, depth);
    var center = new Vector(
        westMostLocation + width / 2,
        lowestBlock + height / 2,
        northMostLocation + depth / 2
    );

    return Optional.of(new GeometryInfo(size, center));
  }

  /**
   * Creates a {@link GeometryInfo} from a {@link AreaCube}
   */
  public static GeometryInfo fromAreaCube(AreaCube areaCube) {
    var origin = LocationUtils.convertToVector(areaCube.getOrigin());
    var size = LocationUtils.convertToVector(areaCube.getSize());

    var center = new Vector(
        origin.getX() + size.getX() / 2,
        origin.getY() + size.getY() / 2,
        origin.getZ() + size.getZ() / 2
    );

    return new GeometryInfo(size, center);
  }

  /**
   * Get the corner of the cube represented by the current instance where all dimensions have their
   * maximum value.
   */
  public Vector getMaxCorner() {
    return center.clone().add(size.clone().multiply(0.5));
  }

  /**
   * Get the corner of the cube represented by the current instance where all dimensions have their
   * minimum value.
   */
  public Vector getMinCorner() {
    return center.clone().subtract(size.clone().multiply(0.5));
  }

  /**
   * Returns true if the specified point is found in the XZ column defined by this instance.
   */
  public boolean isPointInXZColumnDefinedByGeometry(Vector point) {
    var minCorner = getMinCorner();
    var maxCorner = getMaxCorner();

    return point.getX() >= minCorner.getX() && point.getX() <= maxCorner.getX()
        && point.getZ() >= minCorner.getZ() && point.getZ() <= maxCorner.getZ();
  }

  /**
   * Modifies the dimensions of this instance so that it includes the other instance.
   */
  public void extendSelfToIncludeOther(GeometryInfo other) {
    var minCorner = getMinCorner();
    var maxCorner = getMaxCorner();

    var otherMinCorner = other.getMinCorner();
    var otherMaxCorner = other.getMaxCorner();

    var newMinCorner = new Vector(
        Math.min(minCorner.getX(), otherMinCorner.getX()),
        Math.min(minCorner.getY(), otherMinCorner.getY()),
        Math.min(minCorner.getZ(), otherMinCorner.getZ())
    );

    var newMaxCorner = new Vector(
        Math.max(maxCorner.getX(), otherMaxCorner.getX()),
        Math.max(maxCorner.getY(), otherMaxCorner.getY()),
        Math.max(maxCorner.getZ(), otherMaxCorner.getZ())
    );

    center = newMinCorner
        .clone()
        .add(newMaxCorner
            .clone()
            .subtract(newMinCorner.clone())
            .multiply(0.5));

    size = newMaxCorner
        .clone()
        .subtract(newMinCorner.clone());
  }
}
