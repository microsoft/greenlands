package com.microsoft.plaiground.common.utils;

import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.jetbrains.annotations.Nullable;

/**
 * This class contains multiple utility methods to get/save/check metadata on Minecraft entities.
 */
public class MetadataUtils {

  private static final String _PREFIX = "PlayGround_MDT_";

  /**
   * Gets the metadata value from the entity for the specified key. Return null if the key is not
   * found.
   *
   * @param entity the entity to get the metadata from
   * @param metadataKey the metadata key to get
   * @return the for the specified key
   */
  public static @Nullable MetadataValue getEntityMetadata(Metadatable entity, Enum<?> metadataKey) {
    var metadata = entity.getMetadata(_PREFIX + metadataKey.name());

    if (!metadata.isEmpty()) {
      return metadata.get(0);
    } else {
      return null;
    }
  }

  /**
   * Same as {@link #getEntityMetadata(Metadatable, Enum)} but returns a string instead of a {@link
   * MetadataValue} instance.
   */
  public static @Nullable String getEntityMetadataAsString(Metadatable entity,
      Enum<?> metadataKey) {
    var metadata = getEntityMetadata(entity, metadataKey);
    if (metadata != null) {
      return metadata.asString();
    } else {
      return null;
    }
  }

  public static boolean entityHasMetadata(Metadatable entity, Enum<?> metadataKey) {
    return getEntityMetadata(entity, metadataKey) != null;
  }

  public static boolean doesEntityMetadataHaveValue(Metadatable entity, Enum<?> metadataKey,
      Enum<?> value) {
    var stringValue = getEntityMetadataAsString(entity, metadataKey);
    return stringValue != null && stringValue.equals(value.name());
  }

  public static void setEntityMetadata(Metadatable entity, Enum<?> metadataKey, String value) {
    entity.setMetadata(_PREFIX + metadataKey.name(),
        new FixedMetadataValue(PluginUtils.getPluginInstance(), value));
  }

  public static void setEntityMetadata(Metadatable entity, Enum<?> metadataKey, Enum<?> value) {
    setEntityMetadata(entity, metadataKey, value.name());
  }

  /**
   * Removes metadata from entity if it has it. If entity doesn't have the specified metadata key
   * then nothing is done.
   *
   * @param entity the entity we want to remove the metadata key from
   * @param metadataKey the key we want to remove
   */
  public static void removeEntityMetadata(Metadatable entity, Enum<?> metadataKey) {
    if (entityHasMetadata(entity, metadataKey)) {
      entity.removeMetadata(_PREFIX + metadataKey.name(), PluginUtils.getPluginInstance());
    }
  }
}
