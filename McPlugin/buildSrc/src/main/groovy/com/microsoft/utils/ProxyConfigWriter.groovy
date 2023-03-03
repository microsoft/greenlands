package com.microsoft.utils

import com.microsoft.Constants
import org.gradle.api.Project

import java.nio.file.Files

class ProxyConfigWriter {
    static void writeProxyStartScript(String proxyPath) {
        var proxyName = Constants.PROJECT_ROOT_DIR.resolve(proxyPath).fileName
        var start_contents = """
\$host.ui.RawUI.WindowTitle = \"$proxyName\"
cp velocity-local.toml velocity.toml
java -Xmx${Constants.PROXY_RAM} -Xms${Constants.PROXY_RAM} -XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:+UnlockExperimentalVMOptions -XX:+ParallelRefProcEnabled -XX:+AlwaysPreTouch -jar velocity.jar
"""
        var start_file = Constants.PROJECT_ROOT_DIR.resolve("$proxyPath/start.ps1").toFile()
        start_file.write(start_contents)
    }

    static void writeProxyConfig(String proxyName, String[] serverList, Project project, String mainServer) {
        var proxyPort = ConfigUtils.getNextProxyQueryPort().toString()

        var configTemplate = Files.readString(Constants.PROJECT_ROOT_DIR.resolve("buildSrc/src/main/groovy/com/microsoft/templates/proxy/velocity.toml"))
        configTemplate = configTemplate.replace("{{QUERY_PORT}}", proxyPort)
        configTemplate = configTemplate.replace("{{MAIN_SERVER}}", mainServer)

        var serversStringK8s = ""
        var serversStringLocal = ""
        for (server in serverList) {

            // TODO this is very very flimsy
            var serverPort = Files.readString(Constants.PROJECT_ROOT_DIR.resolve("minecraft-server/servers/$server/server.properties")).split("\n").find {
                it.startsWith("server-port=")
            }.split("=")[1].strip()

            serversStringK8s += """
  $server = "${server.toLowerCase()}-service:${serverPort}" """

            serversStringLocal += """
  $server = "localhost:${serverPort}" """
        }

        var configTemplateK8s = configTemplate.replace("{{SERVER_LIST}}", serversStringK8s)
        var configTemplateLocal = configTemplate.replace("{{SERVER_LIST}}", serversStringLocal)

        Constants.PROJECT_ROOT_DIR.resolve("minecraft-server/proxies/$proxyName/velocity-k8s.toml").toFile().write(configTemplateK8s)
        Constants.PROJECT_ROOT_DIR.resolve("minecraft-server/proxies/$proxyName/velocity-local.toml").toFile().write(configTemplateLocal)


        // write dockerfile
        var dockerFileTemplate = Files.readString(Constants.PROJECT_ROOT_DIR.resolve("buildSrc/src/main/groovy/com/microsoft/templates/proxy/Dockerfile.proxy"))
        dockerFileTemplate = dockerFileTemplate.replace("{{QUERY_PORT}}", proxyPort)
        dockerFileTemplate = dockerFileTemplate.replace("{{PROXY_NAME}}", proxyName.toLowerCase())
        dockerFileTemplate = dockerFileTemplate.replace("{{PROXY_MEMORY}}", Constants.PROXY_RAM)
        Constants.PROJECT_ROOT_DIR.resolve("minecraft-server/proxies/$proxyName/Dockerfile").toFile().write(dockerFileTemplate)

        // write k8s definition
        var k8sTemplate = Files.readString(Constants.PROJECT_ROOT_DIR.resolve("buildSrc/src/main/groovy/com/microsoft/templates/proxy/k8s-proxy-pod.yaml"))
        k8sTemplate = k8sTemplate.replace("{{QUERY_PORT}}", proxyPort)
        k8sTemplate = k8sTemplate.replace("{{PROXY_NAME}}", proxyName.toLowerCase())
        k8sTemplate = k8sTemplate.replace("{{ACR_URL}}", Constants.ACR_URL)
        Constants.PROJECT_ROOT_DIR.resolve("minecraft-server/proxies/$proxyName/k8s-proxy-pod.yaml").toFile().write(k8sTemplate)

        // write forwarding.secret file
        var forwardingSecret = "Wcz9gvrtSdG4"
        Constants.PROJECT_ROOT_DIR.resolve("minecraft-server/proxies/$proxyName/forwarding.secret").toFile().write(forwardingSecret)
    }
}
