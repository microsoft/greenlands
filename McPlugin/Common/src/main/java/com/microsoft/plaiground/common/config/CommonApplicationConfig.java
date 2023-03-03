package com.microsoft.plaiground.common.config;

import com.microsoft.plaiground.common.config.CommonApplicationConfigProvider.AuthenticationSettings;
import com.microsoft.plaiground.common.config.CommonApplicationConfigProvider.AzureStorageSettings;
import com.microsoft.plaiground.common.config.CommonApplicationConfigProvider.EnvironmentSettings;
import com.microsoft.plaiground.common.config.CommonApplicationConfigProvider.EventHubSettings;
import com.microsoft.plaiground.common.config.CommonApplicationConfigProvider.PlaigroundApiSettings;
import com.microsoft.plaiground.common.config.CommonApplicationConfigProvider.RedisSettings;

/**
 *
 */
public interface CommonApplicationConfig {

  public AuthenticationSettings authenticationSettings();

  public EventHubSettings eventHubSettings();

  public RedisSettings redisSettings();

  public PlaigroundApiSettings plaigroundApiSettings();

  public AzureStorageSettings azureStorageSettings();

  public EnvironmentSettings environmentSettings();
}
