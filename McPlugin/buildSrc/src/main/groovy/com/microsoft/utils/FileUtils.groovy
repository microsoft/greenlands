package com.microsoft.utils

import com.microsoft.Constants
import com.microsoft.RemoteJar
import org.gradle.api.tasks.Copy

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class FileUtils {
    static void copyFile(Path source, Path destination) {
        var copy = new Copy();
        copy.from(source)
        copy.into(source)
    }

    static void downloadFile(String url, String path) {
        var destination = Constants.PROJECT_ROOT_DIR.toAbsolutePath().resolve(path)
        println("Downloading file to $destination from URL $url")
        var inputBuffer = new BufferedInputStream(new URL(url).openStream())
        Files.copy(inputBuffer, destination, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Returns true if the file was downloaded, false otherwise
     * */
    static boolean downloadFileIfNotExists(RemoteJar fileJar, String destinationPath) {
        var haveToDownloadFile = !Constants.PROJECT_ROOT_DIR.resolve(destinationPath).toFile().exists()

        if (haveToDownloadFile) {
            downloadFile(fileJar.url, destinationPath)
        } else {
            println("Not downloading $fileJar.url since it already exists")
        }

        checkChecksumOfFile(destinationPath, fileJar.sha256Checksum)

        return haveToDownloadFile
    }

    static void downloadPluginIfNotExist(RemoteJar pluginJar, String destinationDir) {
        var pluginName = pluginJar.url.split("/").last()
        var pluginPath = "$destinationDir/$pluginName"

        downloadFileIfNotExists(pluginJar, pluginPath)
    }

    /**
     * Given a path to a file and a sha256 checksum, this method will check if the file matches the checksum
     * If the file does not match the checksum, an exception will be thrown
     * */
    static void checkChecksumOfFile(String filePath, String sha256Checksum) {
        var file = new File(filePath)
        if (!file.exists()) {
            throw new RuntimeException("Tried to check checksum of file but file $filePath does not exist")
        }

        var fileChecksum = MessageDigest.getInstance("SHA-256").digest(file.bytes)
        var fileChecksumString = fileChecksum.encodeHex().toString()

        // raise an exception if the checksums don't match
        if (fileChecksumString != sha256Checksum) {
            throw new RuntimeException(
                    "Checksum of file $filePath doesn't match. Expected $sha256Checksum but got $fileChecksumString. Try deleting the file and running the task again."
            )
        }
    }

    static void deleteFileOrDirectory(File to_delete) {
        if (to_delete.isDirectory()) {
            to_delete.deleteDir()
        } else {
            to_delete.delete()
        }
    }

    /**
     * Creates folder if it doesn't exist
     * @param folder
     * @return returns `true` if the folder was created
     */
    static boolean createFolderIfNotExist(String folder) {
        var folderEntry = Constants.PROJECT_ROOT_DIR.resolve(folder).toFile()
        if (folderEntry.exists()) {
            return false
        } else {
            println("Trying to create dir: $folder")
            return folderEntry.mkdirs()
        }
    }
}
