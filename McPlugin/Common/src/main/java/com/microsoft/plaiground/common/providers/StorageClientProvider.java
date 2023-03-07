package com.microsoft.greenlands.common.providers;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.microsoft.greenlands.common.config.CommonApplicationConfig;

public class StorageClientProvider {

    private static CommonApplicationConfig AppConfig = null;

    public static void initialize(CommonApplicationConfig appConfig) {
        AppConfig = appConfig;
    }

    // https://docs.microsoft.com/en-us/java/api/overview/azure/storage-blob-readme?view=azure-java-stable#create-a-blobcontainerclient
    public static BlobContainerClient getTaskDataBlobContainerClient() {
        var azureStorageSettings = AppConfig.azureStorageSettings();
        var blobContainerClient = new BlobContainerClientBuilder()
            .connectionString(azureStorageSettings.connectionString())
            .containerName(azureStorageSettings.taskDataContainerName())
            .buildClient();

        return blobContainerClient;
    }
}
