package com.microsoft.greenlands.common.config;

import com.microsoft.greenlands.common.config.CommonApplicationConfigProvider.AuthenticationSettings;
import com.microsoft.greenlands.common.config.CommonApplicationConfigProvider.AzureStorageSettings;
import com.microsoft.greenlands.common.config.CommonApplicationConfigProvider.EnvironmentSettings;
import com.microsoft.greenlands.common.config.CommonApplicationConfigProvider.EventHubSettings;
import com.microsoft.greenlands.common.config.CommonApplicationConfigProvider.GreenlandsApiSettings;
import com.microsoft.greenlands.common.config.CommonApplicationConfigProvider.RedisSettings;

/**
 *
 */
public interface CommonApplicationConfig {

  public AuthenticationSettings authenticationSettings();

  public EventHubSettings eventHubSettings();

  public RedisSettings redisSettings();

  public GreenlandsApiSettings greenlandsApiSettings();

  public AzureStorageSettings azureStorageSettings();

  public EnvironmentSettings environmentSettings();
}
