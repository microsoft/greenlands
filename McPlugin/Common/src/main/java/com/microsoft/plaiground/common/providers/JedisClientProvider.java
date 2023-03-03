package com.microsoft.plaiground.common.providers;

import com.microsoft.plaiground.common.config.CommonApplicationConfig;
import com.microsoft.plaiground.common.data.RecordSerializer;
import com.microsoft.plaiground.common.data.RedisRecord;
import com.microsoft.plaiground.common.data.records.GameConfig;
import com.microsoft.plaiground.common.data.records.PlayerGameConfig;
import com.microsoft.plaiground.common.data.records.TaskEditSession;
import com.microsoft.plaiground.common.utils.MinecraftLogger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisClientProvider implements JedisClient {

  private static JedisClientProvider _instance;
  private final JedisPool pool;

  public JedisClientProvider(CommonApplicationConfig appConfig) {
    var configJedis = new JedisPoolConfig();

    configJedis.setMaxTotal(5);
    configJedis.setMaxWaitMillis(800);
    configJedis.setBlockWhenExhausted(true);
    configJedis.setTestOnCreate(true);

    var settings = appConfig.redisSettings();
    pool = new JedisPool(configJedis, settings.host(), settings.port());

    _instance = this;
  }


  /**
   * Gets the singleton instance of {@link JedisClient}. If JedisClient hasn't yet been
   * instantiated then this with throw an {@link AssertionError};
   */
  public static @Nonnull JedisClientProvider getInstance() {
    assert _instance != null :
        "Tried to get JedisClient instance but it is not set. "
        + "Has it been instantiated on plugin onEnable?";

    return _instance;
  }

  /**
   * Closes Jedis client pool and removes {@link JedisClientProvider#_instance}. This method should
   * be called only when the {@link org.bukkit.plugin.java.JavaPlugin} that uses it is being shut
   * down.
   */
  @Override
  public void closePool() {
    MinecraftLogger.info("Shutting down Redis client");
    _instance = null;
    pool.close();
  }

  private <T> T runWithResource(@Nonnull Function<Jedis, T> jedisConsumer) {
    var resource = pool.getResource();
    var result = jedisConsumer.apply(resource);

    resource.close();
    return result;
  }

  /**
   * Sets a key in Redis to the specified value.
   */
  public void setKey(@Nonnull String key, @Nonnull String value) {
    runWithResource(jedis -> jedis.set(key, value));
  }

  /**
   * Given a {@link HashMap} of keys to values, sets all those keys to the specified values as one
   * Redis operation.
   */
  public void setManyKeys(@Nonnull HashMap<String, Object> keyValues) {
    var concatenated = new ArrayList<String>();

    for (var entry : keyValues.entrySet()) {
      concatenated.add(entry.getKey());
      concatenated.add(entry.getValue().toString());
    }

    runWithResource(jedis -> jedis.mset(concatenated.toArray(new String[concatenated.size()])));
  }

  /**
   * Read the value of a single key from Redis. If the key is not present in Redis then Null is
   * returned.
   */
  public @Nullable String readKey(@Nonnull String key) {
    return runWithResource(jedis -> jedis.get(key));
  }

  /**
   * Reads multiple keys from Redis as a single operation, which is more efficient that making many
   * separate reads.
   *
   * <p>The returned list are the values that correspond to the key at that index, so that
   * `keys[i] = return[i]`</p>
   *
   * <p>If a specific key is not in Redis then Null will be returned.</p>
   */
  public List<@Nullable String> readManyKeys(@Nonnull String... keys) {
    return runWithResource(jedis -> jedis.mget(keys));
  }

  /**
   * Sets the TTL (time to live) after which this key will be removed from Redis. If the key does
   * not exist in Redis then nothing happens.
   * About "pexpire" method refer to document
   * @see <a href="https://redis.io/commands/pexpire/">https://redis.io/commands/pexpire/</a>
   */
  public void setExpireOnKey(@Nonnull String key, long milliseconds) {
    runWithResource(jedis -> jedis.pexpire(key, milliseconds));
  }

  public void deleteKeys(String... keys) {
    runWithResource(jedis -> jedis.del(keys));
  }

  /**
   * Serialize and save a record into Redis.
   */
  public void saveRecord(@Nonnull RedisRecord record) {
    var serialized = RecordSerializer.serialize(record);
    setManyKeys(serialized);
  }

  /**
   * Save a record, same as {@link #saveRecord(RedisRecord)}, but the entries of said record will be
   * deleted from Redis after the specified amount of seconds.(The TTL time per milliseconds)
   */
  public void saveRecordWithExpiration(@Nonnull RedisRecord record, long milliseconds) {
    var serialized = RecordSerializer.serialize(record);
    setManyKeys(serialized);

    for (var k : serialized.keySet()) {
      setExpireOnKey(k, milliseconds);
    }
  }

  /**
   * Provided a {@link RedisRecord} where the {@link
   * com.microsoft.plaiground.common.data.annotations.RedisKey} properties have been set
   * (NOTE: they MUST be set for this to work properly), delete the Redis entries that correspond to
   * that record.
   */
  public void deleteRecord(@Nonnull RedisRecord record) {
    var keys = RecordSerializer.getKeysOfRecord(record);
    deleteKeys(keys.toArray(new String[keys.size()]));
  }

  /**
   * Tries to get the record with the provided type and key(s), if no record is found then NULL is
   * returned. NOTE: the {@link com.microsoft.plaiground.common.data.annotations.RedisKey}
   * properties of the record MUST be set for this to work properly.
   */
  private @Nullable RedisRecord getRecord(@Nonnull RedisRecord recordWithKeyValues) {
    var allKeys = RecordSerializer.getKeysOfRecord(recordWithKeyValues).toArray(new String[0]);
    var individualValues = new HashMap<String, Object>();

    var allValues = readManyKeys(allKeys);

    for (int i = 0; i < allKeys.length; i++) {
      // if there's one key which we didn't find then get operation cannot satisfy record integrity,
      // and we just return null
      if (allValues.get(i) == null) {
        return null;
      }

      individualValues.put(allKeys[i], allValues.get(i));
    }

    return RecordSerializer.deserialize(recordWithKeyValues.getClass(), individualValues);
  }

  /**
   * Gets the {@link PlayerGameConfig} for the specified playerId, if there is one in Redis.
   */
  public @Nullable PlayerGameConfig getPlayerGameConfig(@Nonnull UUID playerId) {
    return (PlayerGameConfig) getRecord(new PlayerGameConfig(playerId.toString()));
  }

  /**
   * Gets the {@link GameConfig} for the specified gameId, if there is one in Redis.
   */
  public @Nullable GameConfig getGameConfig(@Nonnull String gameId) {
    return (GameConfig) getRecord(new GameConfig(gameId));
  }

  /**
   * Gets the {@link TaskEditSession} for the specified authorId, if there is one in Redis.
   */
  public @Nullable TaskEditSession getTaskEditSession(@Nonnull UUID authorId) {
    return (TaskEditSession) getRecord(new TaskEditSession(authorId.toString()));
  }
}