package com.microsoft.plaiground.common.providers;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.plaiground.client.model.Block;
import com.microsoft.plaiground.client.model.GameChanges;
import com.microsoft.plaiground.client.model.GameState;
import com.microsoft.plaiground.common.utils.MinecraftLogger;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class TaskDataProvider {

    private static String getInitialGameStateBlobName(String taskId) {
        return taskId + "/initialGameState.json";
    }

    public static GameState getInitialGameState(String taskId) {
        var blobName = getInitialGameStateBlobName(taskId);
        var objectMapper = PlaigroundServiceApi.getApiClient().getObjectMapper();
        var gameState = new GameState();

        try {
            var blobClient = StorageClientProvider.getTaskDataBlobContainerClient()
                .getBlobClient(blobName);
            MinecraftLogger.finest("Loading " + blobClient.getBlobUrl());
            var initialGameStateString = blobClient
                .downloadContent()
                .toString();

            try {
                gameState = objectMapper.readValue(initialGameStateString, GameState.class);
            } catch (JsonProcessingException e) {
                MinecraftLogger.severe("Failed to convert initial game state string to GameState class for task " + taskId);
                MinecraftLogger.severe(ExceptionUtils.getMessage(e));
                MinecraftLogger.severe(ExceptionUtils.getStackTrace(e));
                MinecraftLogger.severe(initialGameStateString);
            }
        }
        catch (UncheckedIOException ioException) {
            MinecraftLogger.severe("Failed to download blob " + blobName);
            MinecraftLogger.severe(ExceptionUtils.getMessage(ioException));
            MinecraftLogger.severe(ExceptionUtils.getStackTrace(ioException));
        }

        MinecraftLogger.info("Loaded initial game state for task " + taskId);

        return gameState;
    }

    public static void saveInitialGameState(String taskId, GameState initialGameState) {
        var blobName = getInitialGameStateBlobName(taskId);
        var blobClient = StorageClientProvider.getTaskDataBlobContainerClient()
            .getBlobClient(blobName);
        MinecraftLogger.finest("Saving " + blobClient.getBlobUrl());
        var objectMapper = PlaigroundServiceApi.getApiClient().getObjectMapper();

        String preSerializedObject = "{}";
        try {
            preSerializedObject = objectMapper.writeValueAsString(initialGameState);
        } catch (JsonProcessingException e) {
            MinecraftLogger.severe("Failed to serialize initial GameState object for task " + taskId);
            MinecraftLogger.severe(ExceptionUtils.getMessage(e));
            MinecraftLogger.severe(ExceptionUtils.getStackTrace(e));
        }

        var blobData = BinaryData.fromString(preSerializedObject);
        blobClient.upload(blobData, true);

        MinecraftLogger.info("Saved initial game state for task " + taskId);
    }

    private static String getInitialWorldCompleteBlocksBlobName(String taskId) {
        return taskId + "/initialWorldCompleteBlocks.json";
    }

    public static void saveInitialWorldCompleteBlocks(String taskId, Map<String, Block> blocks) {
        var blobName = getInitialWorldCompleteBlocksBlobName(taskId);
        var blobClient = StorageClientProvider.getTaskDataBlobContainerClient()
            .getBlobClient(blobName);
        MinecraftLogger.finest("Saving " + blobClient.getBlobUrl());

        var objectMapper = PlaigroundServiceApi.getApiClient().getObjectMapper();

        String preSerializedObject = "{}";
        try {
            preSerializedObject = objectMapper.writeValueAsString(blocks);
        } catch (JsonProcessingException e) {
            MinecraftLogger.severe("Failed to serialize chunks list for task " + taskId);
            MinecraftLogger.severe(ExceptionUtils.getMessage(e));
            MinecraftLogger.severe(ExceptionUtils.getStackTrace(e));
        }

        var blobData = BinaryData.fromString(preSerializedObject);
        blobClient.upload(blobData, true);

        MinecraftLogger.info("Saved initial world complete blocks for task " + taskId);
    }

    private static String getTargetGameChangesBlobName(String taskId) {
        return taskId + "/targetGameChanges.json";
    }

    public static List<GameChanges> getTargetGameChanges(String taskId) {
        var blobName = getTargetGameChangesBlobName(taskId);
        var objectMapper = PlaigroundServiceApi.getApiClient().getObjectMapper();
        List<GameChanges> gameChangesList = new ArrayList<GameChanges>();

        try {
            var blobClient = StorageClientProvider.getTaskDataBlobContainerClient()
                .getBlobClient(blobName);
            MinecraftLogger.finest("Loading " + blobClient.getBlobUrl());
            var targetGameChangesString = blobClient
                .downloadContent()
                .toString();

            try {
                var gameChangesArray = objectMapper.readValue(targetGameChangesString, GameChanges[].class);
                gameChangesList = Arrays.asList(gameChangesArray);
            } catch (JsonProcessingException e) {
                MinecraftLogger.severe("Failed to convert target game changes string to GameChanges[] class for task " + taskId);
                MinecraftLogger.severe(ExceptionUtils.getMessage(e));
                MinecraftLogger.severe(ExceptionUtils.getStackTrace(e));
                MinecraftLogger.severe(targetGameChangesString);
            }
        }
        catch (UncheckedIOException ioException) {
            MinecraftLogger.severe("Failed to download blob " + blobName);
            MinecraftLogger.severe(ExceptionUtils.getMessage(ioException));
            MinecraftLogger.severe(ExceptionUtils.getStackTrace(ioException));
        }

        MinecraftLogger.info("Loaded target game changes for task " + taskId);

        return gameChangesList;
    }

    public static void saveTargetGameChanges(String taskId, List<GameChanges> targetGameChanges) {
        var blobName = getTargetGameChangesBlobName(taskId);
        var blobClient = StorageClientProvider.getTaskDataBlobContainerClient()
            .getBlobClient(blobName);
        MinecraftLogger.finest("Saving " + blobClient.getBlobUrl());
        var objectMapper = PlaigroundServiceApi.getApiClient().getObjectMapper();

        String preSerializedObject = "[]";
        try {
            preSerializedObject = objectMapper.writeValueAsString(targetGameChanges);
        } catch (JsonProcessingException e) {
            MinecraftLogger.severe("Failed to serialize target game changes for task " + taskId);
            MinecraftLogger.severe(ExceptionUtils.getMessage(e));
            MinecraftLogger.severe(ExceptionUtils.getStackTrace(e));
        }

        var blobData = BinaryData.fromString(preSerializedObject);
        blobClient.upload(blobData, true);

        MinecraftLogger.info("Saved target game changes for task " + taskId);
    }
}
