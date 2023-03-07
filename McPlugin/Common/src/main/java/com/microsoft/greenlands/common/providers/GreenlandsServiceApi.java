package com.microsoft.greenlands.common.providers;

import com.microsoft.greenlands.client.ApiClient;
import com.microsoft.greenlands.client.api.AgentChallengesApi;
import com.microsoft.greenlands.client.api.AgentsApi;
import com.microsoft.greenlands.client.api.GamesApi;
import com.microsoft.greenlands.client.api.HumanChallengesApi;
import com.microsoft.greenlands.client.api.TasksApi;
import com.microsoft.greenlands.client.api.TournamentsApi;
import com.microsoft.greenlands.common.config.CommonApplicationConfig;
import feign.RequestInterceptor;

public class GreenlandsServiceApi {

  private static String apiKeyHeaderName = null;
  private static String apiKey = null;
  private static String serviceHost = null;

  public static void initialize(CommonApplicationConfig appConfig) {
    apiKeyHeaderName = appConfig.authenticationSettings().apiKeyHeaderName();
    apiKey = appConfig.authenticationSettings().apiKey();
    serviceHost = appConfig.greenlandsApiSettings().host();
  }

  public static TournamentsApi tournamentsApi() {
    return getApiClient().buildClient(TournamentsApi.class);
  }

  public static HumanChallengesApi humanChallengesApi() {
    return getApiClient().buildClient(HumanChallengesApi.class);
  }

  public static AgentChallengesApi agentChallengesApi() {
    return getApiClient().buildClient(AgentChallengesApi.class);
  }

  public static AgentsApi agentsApi() {
    return getApiClient().buildClient(AgentsApi.class);
  }

  public static TasksApi tasksApi() {
    return getApiClient().buildClient(TasksApi.class);
  }

  public static GamesApi gamesApi() {
    return getApiClient().buildClient(GamesApi.class);
  }

  public static ApiClient getApiClient() {
    assert serviceHost != null :
        "Tried to get api client instance but serviceHost is null! "
            + "Has it been instantiated on plugin onEnable?";

    var client = new ApiClient();
    client.setBasePath(serviceHost);

    client.getFeignBuilder()
        .dismiss404();

    // For each request, add the api key header.
    RequestInterceptor requestInterceptor = request -> {
      request.header(apiKeyHeaderName, apiKey);
    };

    client.addAuthorization("ApiKey", requestInterceptor);

    return client;
  }
}
