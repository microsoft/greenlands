package com.microsoft.greenlands.common.data;

import com.microsoft.greenlands.common.data.annotations.RedisKey;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import javax.annotation.Nullable;

/**
 * This class is able to serialize/deserialize classes with the {@link RedisRecord} interface into
 * hashmaps that can then be saved to (or read from) Redis.
 *
 * <p>In Redis, every field of a {@link RedisRecord} is represented by a "Redis Key", that has the
 * following structure:</p>
 *
 * <pre>
 * {@code
 * {uppercase class name}:[{field key name}:]{field name}
 * }
 * </pre>
 *
 * <p>The key is divided in segments denoted by the colon ':' character. The last segment is the
 * field name in the {@link RedisRecord}, and everything before the last segment is the "key
 * prefix", which is common for every field in a given class instance.</p>
 *
 * <p>The key prefix is composed of the class name followed by the values of the "key fields"
 * (one segment for each key). A record that is meant to be a singleton can do so by not specifying
 * any keys.</p>
 */
public class RecordSerializer {

  /**
   * Given a class and a {@link HashMap} of redisKeys→values, return a new instance of the class
   * whose fields have the values specified in the hashmap.
   *
   * <p>Example:</p>
   *
   * <pre>
   * {@code
   * deserialize(GameConfig.class, Map.of(
   *    "GAMECONFIG:some-game-id:taskId", "some task id",
   *    "GAMECONFIG:some-game-id:challengeId", "some task id",
   *    "GAMECONFIG:some-game-id:generatorName", "a generator name",
   * ));
   * }
   * </pre>
   *
   * <p>Will return a constructed GameConfig instance with the proper fields set:</p>
   *
   * <pre>
   * {@code
   * GameConfig {
   *    gameId: "some-game-id", // < extracted from the key segment of the key prefix
   *    taskId: "some task id",
   *    challengeId: "some task id",
   *    generatorName: "a generator name",
   * }
   * }
   * </pre>
   */
  public static <T extends RedisRecord> T deserialize(Class<T> clazz,
      HashMap<String, Object> keyValues) {
    var fields = clazz.getFields();

    // get any key to get real values. all keys have the same prefix
    var aKey = (String) keyValues.keySet().toArray()[0];
    var indexOfLastSegmentStart = aKey.lastIndexOf(":");
    var classKeyPrefix = aKey.substring(0, indexOfLastSegmentStart);

    var actualKeyValues = new LinkedList<String>(
        Arrays.stream(classKeyPrefix.split(":")).skip(1).toList()
    );

    var normalizedFieldValueMap = new HashMap<String, Object>();
    for (var entry : keyValues.entrySet()) {
      // field name is always the last element of the key
      var k = entry.getKey().substring(indexOfLastSegmentStart + 1);
      var v = entry.getValue();

      normalizedFieldValueMap.put(k, v);
    }

    // now we create an empty instance and populate it
    T holder = null;
    try {
      holder = clazz.getDeclaredConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException
             | InvocationTargetException | NoSuchMethodException e) {
      System.out.println("Error! Class " + clazz.getName()
          + " has no default constructor which is needed for deserialization!");
      e.printStackTrace();
    }

    for (var f : fields) {
      var redisKey = f.getAnnotation(RedisKey.class);
      try {
        if (redisKey == null) {
          var val = normalizedFieldValueMap.get(f.getName());
          var fieldType = f.getType();

          // if field is nullable and value is empty in REDIS then set field as null
          if (f.getAnnotation(Nullable.class) != null && ((String) val).isEmpty()) {
            f.set(holder, null);
            continue;
          }

          // deserialized based on field type
          if (fieldType.isArray()) {
            var stringValue = ((String) val);
            if (!stringValue.isEmpty()) {
              f.set(holder, stringValue.split(","));
            } else {
              f.set(holder, new String[0]);
            }
          } else if (fieldType.equals(boolean.class) || fieldType.equals(Boolean.class)) {
            f.set(holder, Boolean.parseBoolean((String) val));
          } else if (fieldType.equals(int.class) || fieldType.equals(Integer.class)){
            f.set(holder, Integer.parseInt((String) val));
          } else if (fieldType.isEnum()) {
            var matchedEnum = false;

            for (var enumValue : fieldType.getEnumConstants()) {
              if (((Enum<?>) enumValue).name().equals(val)) {
                f.set(holder, enumValue);
                matchedEnum = true;
                break;
              }
            }

            assert matchedEnum : "Provided value " + val + " is not a valid value for the enum "
                + fieldType.getName();
          } else {
            f.set(holder, val);
          }
        } else {
          // keys are serialized/deserialized always in the same order, so we can just pop them
          // from the array
          f.set(holder, actualKeyValues.pop());
        }
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }

    return holder;
  }

  /**
   * Given a {@link RedisRecord} instance (which MUST have the key fields set) return the set of all
   * Redis keys that represent every field in the record.
   *
   * <p>Example:</p>
   *
   * <p>Given an initialized {@link RedisRecord}:</p>
   *
   * <pre>
   * {@code
   * getKeysOfRecord(GameConfig {
   *    gameId: "some-game-id", // this is the key field of the class
   *    taskId: "some task id",
   *    challengeId: "some task id",
   *    generatorName: "a generator name",
   * })
   * }
   * </pre>
   *
   * <p>It will return the following set of keys:</p>
   *
   * <pre>
   * {@code
   *  GAMECONFIG:some-game-id:taskId
   *  GAMECONFIG:some-game-id:challengeId
   *  GAMECONFIG:some-game-id:generatorName
   * }
   * </pre>
   */
  public static HashSet<String> getKeysOfRecord(RedisRecord record) {
    var fields = record.getClass().getFields();
    var valueFields = new ArrayList<Field>();

    StringBuilder classKeyPrefixBuilder = new StringBuilder(
        record.getClass().getSimpleName().toUpperCase() + ":");

    for (var f : fields) {
      var redisKey = f.getAnnotation(RedisKey.class);
      if (redisKey == null) {
        valueFields.add(f);
      } else {
        try {
          classKeyPrefixBuilder.append(f.get(record).toString());
          classKeyPrefixBuilder.append(":");
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }

    var classKeyPrefix = classKeyPrefixBuilder.toString();

    var result = new HashSet<String>();
    for (var vf : valueFields) {
      var k = classKeyPrefix + vf.getName();
      result.add(k);
    }

    return result;

  }

  /**
   * Given a {@link RedisRecord} instance return a {@link HashMap} where every value is the value of
   * a field in the record and every key is the Redis key for that field.
   *
   * <p>The keys of the returned HashMap are the same that one would get from {@link
   * RecordSerializer#getKeysOfRecord(RedisRecord)}, and the values are the values of the
   * corresponding field in the provided record.</p>
   *
   * <p>Example:</p>
   *
   * <pre>
   * {@code
   * serialize(GameConfig {
   *    gameId: "some-game-id", // < this is the key field
   *    taskId: "some task id",
   *    challengeId: "some task id",
   *    generatorName: "a generator name",
   * })
   * }
   * </pre>
   *
   * <p>Would return:</p>
   *
   * <pre>
   * {@code
   * {
   *    "GAMECONFIG:some-game-id:taskId": "some task id",
   *    "GAMECONFIG:some-game-id:challengeId": "some task id",
   *    "GAMECONFIG:some-game-id:generatorName": "a generator name",
   * }
   * }
   * </pre>
   */
  public static HashMap<String, Object> serialize(RedisRecord record) {
    var fields = record.getClass().getFields();
    var valueFields = new ArrayList<Field>();

    StringBuilder classKeyPrefixBuilder = new StringBuilder(
        record.getClass().getSimpleName().toUpperCase() + ":");

    for (var f : fields) {
      var redisKey = f.getAnnotation(RedisKey.class);
      if (redisKey == null) {
        valueFields.add(f);
      } else {
        try {
          classKeyPrefixBuilder.append(f.get(record).toString());
          classKeyPrefixBuilder.append(":");
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }

    var classKeyPrefix = classKeyPrefixBuilder.toString();

    var result = new HashMap<String, Object>();
    for (var vf : valueFields) {
      try {
        var k = classKeyPrefix + vf.getName();
        var v = vf.get(record);
        var fieldType = vf.getType();

        // if field is nullable and value is null then just save an empty string
        if (vf.getAnnotation(Nullable.class) != null && v == null) {
          result.put(k, "");
          continue;
        }

        // serialize based on field type
        if (fieldType.isArray()) {
          result.put(k, String.join(",", (String[]) v));
        } else if (fieldType.equals(boolean.class) || fieldType.equals(Boolean.class)) {
          result.put(k, Boolean.toString((boolean) v));
        } else if (fieldType.equals(int.class) || fieldType.equals(Integer.class)) {
          result.put(k, Integer.toString((int) v));
        } else if (fieldType.isEnum()) {
          result.put(k, ((Enum<?>) v).name());
        } else {
          result.put(k, v);
        }

      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    }

    return result;
  }
}
