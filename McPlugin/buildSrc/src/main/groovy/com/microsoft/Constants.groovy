package com.microsoft

import org.gradle.api.Project

import java.nio.file.Path

class RemoteJar {
    String url
    String sha256Checksum

    RemoteJar(String url, String sha256Checksum) {
        this.url = url
        this.sha256Checksum = sha256Checksum
    }
}

class Constants {
    // No need to set these variables since they will be set automatically
    // whenever a task that uses them is executed. If you ever implement a new
    // task that needs these variables remember to initialize them in the same
    // way as is done for `void scaffoldMinecraft()`
    public static Path PROJECT_ROOT_DIR = null
    public static Project[] ALL_GRADLE_PROJECTS = null

    public static String ACR_URL = "mcpluginacr.azurecr.io"

    // The RAM memory that will be used by allocated by the JVM for the proxy
    // and server respectively. If the proxy/server ends up needing more memory
    // than this then an Out of Memory exception will be thrown and the
    // application will crash.
    // TODO: this should be configurable somewhere (env variable?)
    final public static var PROXY_RAM = "1G"
    final public static var SERVER_RAM = "2G"

    // These are the URLs for the PaperMC and Velocity Jars. These are the
    // origins that the scaffolding process will use to download these jars.
    final public static var PAPER_JAR = new RemoteJar("https://api.papermc.io/v2/projects/paper/versions/1.19.2/builds/191/downloads/paper-1.19.2-191.jar",
            "0b08ef9ecf458717a954c2baedd386ce268e5c587de32e16f3c4cb4cfe7698f9")

    final public static var VELOCITY_JAR = new RemoteJar("https://api.papermc.io/v2/projects/velocity/versions/3.1.2-SNAPSHOT/builds/184/downloads/velocity-3.1.2-SNAPSHOT-184.jar",
            "ad0de85eb492a767de76aeb5d418229c43335bb06bad556971d11ccf1386e116")

    // Array of the default plugins that should be added to all servers
    final public static var PLUGINS_JARS = [
            new RemoteJar("https://media.forgecdn.net/files/3462/546/Multiverse-Core-4.3.1.jar",
                    "38c8b6a6aa168ae6a09cc0c9f77115ea975768410bc107c4ce0b32de1bebc787"),
            new RemoteJar("https://media.forgecdn.net/files/3596/715/CleanroomGenerator-1.2.1.jar",
                    "40d1befed8f6d5805dc9085667998c83099b658136b2f62d96e130e92b4cf3f2"),
            new RemoteJar("https://ci.citizensnpcs.co/job/Citizens2/2675/artifact/dist/target/Citizens-2.0.30-b2675.jar",
                    "71acb299fbec2ad78772ef66f25cc5e5edb672a37677019ab82dc7f4b99f9ba3")
    ]

    // Arrays that specify the servers that need to be created during scaffolding
    final public static var ALL_SERVERS_TO_CREATE = ["LobbyServer",
                                                     "GameServer-1",
    ]

}
