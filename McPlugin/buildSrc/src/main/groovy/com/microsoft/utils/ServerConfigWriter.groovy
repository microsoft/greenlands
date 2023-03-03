package com.microsoft.utils

import com.microsoft.Constants
import org.gradle.api.Project

import java.nio.file.Files

class ServerConfigWriter {
    static void writeAcceptedEula(String serverPath) {
        println("Writing pre-accepted EULA to $serverPath")
        var eulaFile = Constants.PROJECT_ROOT_DIR.resolve("$serverPath/eula.txt").toFile()
        eulaFile.write("eula=true\n")
    }

    static void writeServerStartScript(String serverPath) {
        var serverName = Constants.PROJECT_ROOT_DIR.resolve(serverPath).getFileName().toString()
        var templateDir = Constants.PROJECT_ROOT_DIR.resolve("buildSrc/src/main/groovy/com/microsoft/templates/server/")

        println("Writing server start script for $serverPath")
        var startScriptTemplate = Files.readString(templateDir.resolve("start.template.ps1"))
        startScriptTemplate = startScriptTemplate.replace("{{SERVER_NAME}}", serverName)
        startScriptTemplate = startScriptTemplate.replace("{{SERVER_MEMORY}}", Constants.SERVER_RAM)
        var start_file = Constants.PROJECT_ROOT_DIR.resolve("$serverPath/start.ps1").toFile()
        start_file.write(startScriptTemplate)

        println("Writing server debug script for $serverPath")
        var debugScriptTemplate = Files.readString(templateDir.resolve("debug.template.ps1"))
        debugScriptTemplate = debugScriptTemplate.replace("{{SERVER_NAME}}", serverName)
        debugScriptTemplate = debugScriptTemplate.replace("{{SERVER_MEMORY}}", Constants.SERVER_RAM)
        var debug_file = Constants.PROJECT_ROOT_DIR.resolve("$serverPath/debug.ps1").toFile()
        debug_file.write(debugScriptTemplate)
    }

    static void writeServerConfiguration(String serverPathStr, Project project) {
        // config for server to work properly with proxy
        project.copy {
            from("buildSrc/src/main/groovy/com/microsoft/templates/server/spigot.yml")
            into("$serverPathStr/")
        }

        project.copy {
            from("buildSrc/src/main/groovy/com/microsoft/templates/server/paper.yml")
            into("$serverPathStr/")
        }

        project.copy {
            from("buildSrc/src/main/groovy/com/microsoft/templates/server/bukkit.yml")
            into("$serverPathStr/")
        }

        project.copy {
            from("buildSrc/src/main/groovy/com/microsoft/templates/server/.dockerignore")
            into("$serverPathStr/")
        }

        var templateDir = Constants.PROJECT_ROOT_DIR.resolve("buildSrc/src/main/groovy/com/microsoft/templates/server/")
        var outputPath = Constants.PROJECT_ROOT_DIR.resolve(serverPathStr)

        // write server properties
        var serverPort = ConfigUtils.getNextServerPort().toString()
        var servProperties = Files.readString(templateDir.resolve("server.properties"))
        servProperties = servProperties.replace("{{RCON_PORT}}", ConfigUtils.getNextRconPort().toString())
        servProperties = servProperties.replace("{{QUERY_PORT}}", ConfigUtils.getNextQueryPort().toString())
        servProperties = servProperties.replace("{{SERVER_PORT}}", serverPort)
        Files.writeString(outputPath.resolve("server.properties"), servProperties)

        // write Dockerfile
        var serverName = outputPath.getFileName().toString().toLowerCase()
        var dockerTemplate = Files.readString(templateDir.resolve("Dockerfile.server"))
        dockerTemplate = dockerTemplate.replace("{{SERVER_NAME}}", serverName)
        dockerTemplate = dockerTemplate.replace("{{SERVER_MEMORY}}", Constants.SERVER_RAM)
        Files.writeString(outputPath.resolve("Dockerfile"), dockerTemplate)

        // write k8s definition
        /// copy .env variables from current process
        var envVariables = ""
        for (entry in ConfigUtils.getDotEnvEntries().entrySet()) {
            envVariables += """
            - name: $entry.key
              value: "$entry.value"
"""
        }

        var k8sTemplate = Files.readString(templateDir.resolve("k8s-server-pod.yaml"))
        k8sTemplate = k8sTemplate.replace("{{SERVER_NAME}}", serverName)
        k8sTemplate = k8sTemplate.replace("{{SERVER_PORT}}", serverPort)
        k8sTemplate = k8sTemplate.replace("{{ENV_VARIABLES}}", envVariables)
        k8sTemplate = k8sTemplate.replace("{{ACR_URL}}", Constants.ACR_URL)
        Files.writeString(outputPath.resolve("k8s-server-pod.yaml"), k8sTemplate)
    }
}
