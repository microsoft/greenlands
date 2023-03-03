package com.microsoft.tasks

import com.microsoft.Constants
import com.microsoft.utils.FileUtils
import com.microsoft.utils.ProxyConfigWriter
import com.microsoft.utils.ServerConfigWriter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Input

import java.nio.file.Files

class ScaffoldMcTask extends DefaultTask {

    private String acrUrlParam

    @Option(option = "acr_url",
            description = "The URL to use for the Azure Container Registry. Defaults to mcpluginacr.azurecr.io.")
    public void setAcrUrlParam(String acrUrlParam) {
        this.acrUrlParam = acrUrlParam
    }

    @Input
    @Optional
    public String getAcrUrlParam() {
        return acrUrlParam
    }

    @TaskAction
    void scaffoldMinecraft() {
        Constants.PROJECT_ROOT_DIR = project.getRootDir().toPath()
        Constants.ALL_GRADLE_PROJECTS = project.getAllprojects()

        Constants.ACR_URL = acrUrlParam != null ? acrUrlParam : "mcpluginacr.azurecr.io"

        // all operations are idempotent so we can run this as many times as we want
        // TODO lots of hardcoded strings, move them to constants
        FileUtils.createFolderIfNotExist("minecraft-server")

        createInitialFolders()
        downloadCacheJars()
        makeServers()
        makeProxies()
        writeSkaffoldTemplate()
        writeStartAllScript()
    }

    private static void createInitialFolders() {
        for (folder in ["minecraft-server/proxies", "minecraft-server/servers", "mc-cache", "mc-cache/plugins"]) {
            FileUtils.createFolderIfNotExist(folder)
        }
    }

    private void downloadCacheJars() {
        // if server was downloaded then we need to patch vanilla jar
        if (FileUtils.downloadFileIfNotExists(Constants.PAPER_JAR, "mc-cache/server.jar")) {
            // running the server in this way will ensure that a patched official server is created
            project.exec {
                workingDir "${project.rootDir}/mc-cache"
                executable 'java'
                args(["-jar", "server.jar", "--help"])
            }
        }

        FileUtils.downloadFileIfNotExists(Constants.VELOCITY_JAR, "mc-cache/velocity.jar")

        for (pluginUrl in Constants.PLUGINS_JARS) {
            FileUtils.downloadPluginIfNotExist(pluginUrl, "mc-cache/plugins")
        }
    }

    private void makeServers() {
        for (serverName in Constants.ALL_SERVERS_TO_CREATE) {
            var serverPath = "minecraft-server/servers/$serverName"
            FileUtils.createFolderIfNotExist(serverPath)
            FileUtils.createFolderIfNotExist("$serverPath/plugins")
            FileUtils.createFolderIfNotExist("$serverPath/cache")

            project.copy {
                from("mc-cache/server.jar")
                into("$serverPath/")
            }

            project.copy {
                from("mc-cache/plugins/")
                into("$serverPath/plugins/")
            }

            project.copy {
                from("mc-cache/cache/")
                into("$serverPath/cache/")
            }

            ServerConfigWriter.writeAcceptedEula(serverPath)
            ServerConfigWriter.writeServerStartScript(serverPath)
            ServerConfigWriter.writeServerConfiguration(serverPath, project)
        }
    }

    private void writeGameProxyConfig() {
        ProxyConfigWriter.writeProxyConfig("gameProxy", Constants.ALL_SERVERS_TO_CREATE as String[], project, "LobbyServer")
    }

    private void makeProxies() {
        for (proxyName in ["gameProxy"]) {
            var proxyPath = "minecraft-server/proxies/$proxyName"
            FileUtils.createFolderIfNotExist(proxyPath)

            project.copy {
                from("mc-cache/velocity.jar")
                into("$proxyPath/")
            }

            ProxyConfigWriter.writeProxyStartScript(proxyPath)
        }

        writeGameProxyConfig()
    }

    private void writeSkaffoldTemplate() {
        var artifactString = ""
        var manifestString = ""

        for (proxyName in ["gameProxy"]) {
            manifestString += """
    - proxies/$proxyName/k8s-proxy-pod.yaml
"""

            artifactString += """
  - image: ${Constants.ACR_URL}/proxy/${proxyName.toLowerCase()}-image
    context: proxies/$proxyName
    docker:
      dockerfile: Dockerfile
"""
        }

        for (serverName in Constants.ALL_SERVERS_TO_CREATE) {
            var lcServerName = serverName.toLowerCase()
            artifactString += """
  - image: ${Constants.ACR_URL}/server/$lcServerName-image
    context: servers/$serverName
    docker:
      dockerfile: Dockerfile
"""
            manifestString += """
    - servers/$serverName/k8s-server-pod.yaml
"""
        }

        var skaffoldTempalte = Files.readString(Constants.PROJECT_ROOT_DIR.resolve("buildSrc/src/main/groovy/com/microsoft/templates/other/skaffold.template.yaml"))
        skaffoldTempalte = skaffoldTempalte.replace("{{ARTIFACTS}}", artifactString)
        skaffoldTempalte = skaffoldTempalte.replace("{{MANIFESTS}}", manifestString)
        Constants.PROJECT_ROOT_DIR.resolve("minecraft-server/skaffold.yaml").toFile().write(skaffoldTempalte)

        project.copy {
            from("buildSrc/src/main/groovy/com/microsoft/templates/other/k8s-redis-pod.yaml")
            into("minecraft-server/")
        }
    }

    static void writeStartAllScript() {
        println("Writing starting script for proxy")

        var proxyStartCommands = ""
        for (proxyName in ["gameProxy"]) {
            proxyStartCommands += """Start-Process pwsh -WorkingDirectory "\${PSScriptRoot}/proxies/$proxyName/" -ArgumentList "start.ps1" \n"""
        }

        // $serverNames = @("LobbyServer", "GameServer-1")
        var serverStartCommands = '$serverNames = @('
        Constants.ALL_SERVERS_TO_CREATE.eachWithIndex { serverName, index ->
            serverStartCommands += '"' + serverName + '"'

            // If not last element then add separator
            if (index != Constants.ALL_SERVERS_TO_CREATE.size() - 1) {
                serverStartCommands += ', '
            }
        }
        serverStartCommands += ')'

        serverStartCommands += """
foreach (\$serverName in \$serverNames) {
    \$serverScriptName = "start.ps1"
    if (\$DebugServerName -eq \$serverName) {
        \$serverScriptName = "debug.ps1"
    }
    echo "Start \$serverName using script name: \$serverScriptName"
    Start-Process pwsh -WorkingDirectory "\${PSScriptRoot}/servers/\$serverName/" -ArgumentList \$serverScriptName
}
"""

        var templateDir = Constants.PROJECT_ROOT_DIR.resolve("buildSrc/src/main/groovy/com/microsoft/templates/other/")
        var startAllScript = Files.readString(templateDir.resolve("main-start.template.ps1"))
        startAllScript = startAllScript.replace("{{PROXY_START_COMMANDS}}", proxyStartCommands)
        startAllScript = startAllScript.replace("{{SERVER_START_COMMANDS}}", serverStartCommands)
        var start_file = Constants.PROJECT_ROOT_DIR.resolve("minecraft-server/start.ps1").toFile()
        start_file.write(startAllScript)
    }

}
