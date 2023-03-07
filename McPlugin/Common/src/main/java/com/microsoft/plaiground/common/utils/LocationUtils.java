package com.microsoft.greenlands.common.utils;

import com.microsoft.greenlands.client.model.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

public class LocationUtils {

  public static com.microsoft.greenlands.client.model.Location fromStringToGreenlandsLocation(
      String locationString) {
    locationString = locationString.replace("[", "");
    locationString = locationString.replace("]", "");
    var coordinates = locationString.split(",");

    var x = Float.parseFloat(coordinates[0]);
    var y = Float.parseFloat(coordinates[1]);
    var z = Float.parseFloat(coordinates[2]);
    // TODO add pitch and yaw here as well

    var location = new Location()
        .x(x)
        .y(y)
        .z(z)
        .pitch(0f)
        .yaw(0f);

    return location;
  }

  public static String fromGreenlandsLocationToString(
      com.microsoft.greenlands.client.model.Location location
  ) {
    // sometimes yaw and pitch can be NaN (e.g. for a block)
    float pitch = location.getPitch() == null || location.getPitch().isNaN() ?
        0.0f
        : location.getPitch();
    float yaw = location.getYaw() == null || location.getYaw().isNaN() ?
        0.0f
        : location.getYaw();

    var locationString =
        "[" + location.getX() + ","
            + location.getY() + ","
            + location.getZ() + ","
            + pitch + ","
            + yaw + "]";

    return locationString;
  }

  /**
   * Greenlands -> Bukkit Location converter.
   **/
  public static org.bukkit.Location convertToBukkitLocation(
      World world,
      com.microsoft.greenlands.client.model.Location greenlandsLoc) {

    return new org.bukkit.Location(world,
        greenlandsLoc.getX(),
        greenlandsLoc.getY(),
        greenlandsLoc.getZ(),
        greenlandsLoc.getYaw(),
        greenlandsLoc.getPitch());
  }

  /**
   * Bukkit -> Greenlands Location converter.
   **/
  public static com.microsoft.greenlands.client.model.Location convertToGreenlandsLocation(
      org.bukkit.Location bukkitLoc) {

    Location location = new Location();
    location.setX((float) bukkitLoc.getX());
    location.setY((float) bukkitLoc.getY());
    location.setZ((float) bukkitLoc.getZ());
    location.setPitch((float) bukkitLoc.getPitch());
    location.setYaw((float) bukkitLoc.getYaw());

    return location;
  }

  public static Vector convertToVector(
      com.microsoft.greenlands.client.model.Location greenlandsLoc
  ) {
    return new Vector(greenlandsLoc.getX(), greenlandsLoc.getY(), greenlandsLoc.getZ());
  }

  public static com.microsoft.greenlands.client.model.Location clone(com.microsoft.greenlands.client.model.Location location) {
    return new Location()
        .x(location.getX())
        .y(location.getY())
        .z(location.getZ())
        .pitch(location.getPitch())
        .yaw(location.getYaw());
  }
}
