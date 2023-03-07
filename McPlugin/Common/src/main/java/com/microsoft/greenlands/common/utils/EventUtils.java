package com.microsoft.greenlands.common.utils;

import java.lang.reflect.InvocationTargetException;
import javax.annotation.Nonnull;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Collection of utilities for handling server events.
 */
public class EventUtils {

  /**
   * Returns the {@link World} on which the event occurred if possible.
   *
   * @param event a server {@link Event}
   * @return The world where the event occurred or Null if the world cannot be inferred.
   */
  @Nullable
  public static World getWorldAssociatedWithEvent(@Nonnull Event event) {
    World world = null;

    try {
      var worldMethod = event.getClass().getMethod("getWorld");
      world = (World) worldMethod.invoke(event);
    } catch (NoSuchMethodException e) {
      try {
        var getUserMethod = event.getClass().getMethod("getPlayer");
        var player = (Player) getUserMethod.invoke(event);
        world = player.getWorld();
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
        ex.printStackTrace();
      }
    } catch (InvocationTargetException | IllegalAccessException e) {
      e.printStackTrace();
    }

    return world;
  }

  public static boolean doesEventOccurInWorld(Event event, String worldName) {
    var world = getWorldAssociatedWithEvent(event);
    return world != null && world.getName().equals(worldName);
  }
}
