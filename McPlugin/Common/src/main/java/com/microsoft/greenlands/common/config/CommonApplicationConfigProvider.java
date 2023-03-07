package com.microsoft.greenlands.common.config;

import javax.annotation.Nullable;

/**
 * This class contains the configuration for the plugin.
 */
public class CommonApplicationConfigProvider implements CommonApplicationConfig {
  /**
   * Configuration Authentication.
   */
  public record AuthenticationSettings(String apiKeyHeaderName, String apiKey) {
  }

  /**
   * Configuration for Azure Storage Connection.
   */
  public record AzureStorageSettings(String connectionString, String taskDataContainerName) {
  }

  /**
   * Configuration for Event Hub.
   */
  public record EventHubSettings(
      String connectionString,
      String name,
      String consumerGroupGameServer,
      String consumerGroupLobbyServer) {
  }

  /**
   * Configuration for the Redis connection.
   */
  public record RedisSettings(String host, int port) {

  }

  /**
   * Configuration to access the Greenlands Service API.
   */
  public record GreenlandsApiSettings(String host) {

  }

  public enum Environment {
    LOCAL,
    DEV,
    PROD
  }

  public record EnvironmentSettings(Environment executionEnvironment) {
    public boolean isDevelopment() {
      return executionEnvironment == Environment.DEV;
    }
  }

  public final AuthenticationSettings authenticationSettings;
  public final AzureStorageSettings azureStorageSettings;
  public final EventHubSettings eventHubSettings;
  public final RedisSettings redisSettings;
  public final GreenlandsApiSettings greenlandsApiSettings;
  public final EnvironmentSettings environmentSettings;

  public CommonApplicationConfigProvider() {
    authenticationSettings = initializeAuthenticationSettings();
    azureStorageSettings = initializeAzureStorageSettings();
    eventHubSettings = initializeEventHubSettings();
    redisSettings = initializeRedisSettings();
    greenlandsApiSettings = initializeGreenlandsApiSettings();
    environmentSettings = initializeEnvironmentSettings();
  }

  @Override
  public AuthenticationSettings authenticationSettings() {
    return authenticationSettings;
  }

  @Override
  public AzureStorageSettings azureStorageSettings() {
    return azureStorageSettings;
  }

  @Override
  public EventHubSettings eventHubSettings() {
    return eventHubSettings;
  }

  @Override
  public RedisSettings redisSettings() {
    return redisSettings;
  }

  @Override
  public GreenlandsApiSettings greenlandsApiSettings() {
    return greenlandsApiSettings;
  }

  @Override
  public EnvironmentSettings environmentSettings() {
    return environmentSettings;
  }

  private GreenlandsApiSettings initializeGreenlandsApiSettings() {
    return new GreenlandsApiSettings(
        getEnvVariable("API_SERVICE_HOST"));
  }

  private RedisSettings initializeRedisSettings() {
    return new RedisSettings(
        getEnvVariable("REDIS_HOST"),
        Integer.parseInt(getEnvVariable("REDIS_PORT")));
  }

  private EventHubSettings initializeEventHubSettings() {
    return new EventHubSettings(
        getEnvVariable("EVENT_HUB_CONNECTION_STRING"),
        getEnvVariable("EVENT_HUB_NAME"),
        getEnvVariable("EVENT_HUB_CONSUMER_GROUP_GAMESERVER"),
        getEnvVariable("EVENT_HUB_CONSUMER_GROUP_LOBBYSERVER"));
  }

  private AuthenticationSettings initializeAuthenticationSettings() {
    return new AuthenticationSettings(
        getEnvVariable("API_KEY_HEADER_NAME"),
        getEnvVariable("API_KEY"));
  }

  private AzureStorageSettings initializeAzureStorageSettings() {
    return new AzureStorageSettings(
        getEnvVariable("STORAGE_CONNECTION_STRING"),
        getEnvVariable("STORAGE_CONTAINER_NAME"));
  }

  private EnvironmentSettings initializeEnvironmentSettings() {
    Environment env;
    var envVar = getEnvVariableWithDefault("ENVIRONMENT", "DEV");
    switch (envVar.toUpperCase()) {
      case "DEV" -> {
        env = Environment.DEV;
      }
      case "PROD" -> {
        env = Environment.PROD;
      }
      default -> {
        throw new RuntimeException("Unknown environment: " + envVar + " valid values: " + Environment.values());
      }
    }

    return new EnvironmentSettings(env);
  }

  private String getEnvVariableWithDefault(String envVariableName, @Nullable String defaultValue) {
    String envVariableValue = System.getenv(envVariableName);
    if (envVariableValue == null) {
      return defaultValue;
    }
    return envVariableValue;
  }

  private String getEnvVariable(String variableName) {
    var value = System.getenv(variableName);
    assert value != null : "Tried to read env variable '" + variableName + "' but it doesn't have a set value";

    return value;
  }
}
