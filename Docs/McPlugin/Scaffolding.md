# Scaffolding Process

- [Scaffolding Process](#scaffolding-process)
  - [Overview of scaffolding process](#overview-of-scaffolding-process)
  - [Configuration settings for scaffolding](#configuration-settings-for-scaffolding)
  - [Copying plugin files to server directory](#copying-plugin-files-to-server-directory)
    - [Naive hot-reload](#naive-hot-reload)


## Overview of scaffolding process

The functionality of scaffolding is implemented as a _Gradle_ task and the code itself can be found under `McPlugin/buildSrc/src/main/groovy/com/microsoft/tasks/ScaffoldMcTask.groovy`. The outcome of this process is the creation of a folder named `minecraft-server` in `McPlugin/minecraft-server`, which will contain the proxy, and servers for each plugin, as well as scripts to start everything.

There's some configuration that specifies which proxies to create, as well as which servers. The configuration for these is automatically created based on some template files found under `buildSrc/src/main/groovy/com/microsoft/templates`.

An example of the structure created by the scaffolding process:

```
minecraft-server
  ├── proxies  ← one folder for each proxy specified in the configuration
  │   └── gameProxy
  │       ├── start.ps1      ← script to start proxy
  │       ├── velocity.jar   ← proxy executable
  │       └── velocity.toml  ← proxy configuration
  ├── servers  ← one folder for each server specified in the configuration
  │   ├── LobbyServer
  │   │    ├── bukkit.yml          ← configuration files for server
  │   │    ├── eula.txt            |
  │   │    ├── paper.yml           |
  │   │    ├── server.properties   |
  │   │    ├── spigot.yml          |
  │   │    ├── server.jar          ← paper server executable
  │   │    ├── cache               ← contains files used by the paper executable
  │   │    │   ├── mojang_1.17.1.jar   ← vanilla Minecraft server
  │   │    │   └── patched_1.17.1.jar  ← Minecraft server patched by Paper
  │   │    ├── plugins  ← jars in this folder will be automatically loaded as plugins by Paper
  │   │    │   ├── LobbyServerPlugin-1.0.0.jar   ← our plugin for this server
  │   │    │   ├── CleanroomGenerator-1.1.1.jar  ← supporting plugins
  │   │    │   └── Multiverse-Core-4.3.1.jar     |
  │   │    ├── debug.ps1  ← starts the server in debug mode so that an external debugger can be attached to the process
  │   │    └── start.ps1  ← starts the server
  │   ├── BuildingServer-1
  │   │   └── ... same as lobby server but with "Building" plugin
  │   └── TaskServer-1
  │       └── ... same as lobby server but with "Task" plugin
  └── start.ps1  ← script that starts proxy and all servers
```

The scaffolding process is idempotent and is composed of the following actions:

1. Download Plugins and Paper Jars
   - Download the jars for the supporting plugins specified in the configuration, the Jar for specified PaperMC version, as well as the Jar for the specified proxy version.
   - These are saved in under `McPluin/mc-cache` so that they don't need to be re-downloaded every time the scaffolding process is done.
     - If the jars exist under this folder then they would just be copied from here instead of re-downloaded.
     - Independently of whether the jars are downloaded or not, the scaffolding process will always check if the jars SHA256 checksum correspond to what it expects (checksum for each jar is defined in `Constants.groovy`).
1. Create servers
   - Looks at the configuration and creates a server folder for each of the specified servers
   - Each server folder will be created with: 
     - The basic configuration to run (ports are chosen dynamically so that they don't conflict with each other) 
     - The Paper server executable
     - A convenience start/debug script
     - Dockerfile and K8s resource definition files
1. Create proxies
   - Looks at the configuration and creates a proxy folder for each of the specified proxies
   -  Each proxy is created with:
      -  Basic configuration which contains the server port as well as the list of known servers and their ports so that the proxy knows how to reach them (these are calculated dynamically)
      - Dockerfile and K8s resource definition files
      - A convenience start/debug script
      - Proxy executable
1. Create a _start all_ script
   - Dynamically check which components have been created until now and create a start script that calls their own start script


## Configuration settings for scaffolding

The configuration for the scaffolding is currently saved as constants in `buildSrc/src/main/groovy/com/microsoft/Constants.groovy`. For the sake of maintainability the documentation of each of these constants is contained directly within the `Constants.groovy` file.

Another important file, configuration-wise, is `buildSrc/src/main/groovy/com/microsoft/utils/ConfigUtils.groovy` which is used to obtain the non-colliding port numbers for the servers and proxies. This is done by means of a series of counters that are incremented every time they're read, and the value of the counters are the ports numbers to use next.


## Copying plugin files to server directory

As said above, each server folder contains a `plugins` folder where the jars of the plugins loaded by the server live. 

```
  LobbyServer
   ├── ... config and cache files omitted
   ├── server.jar          
   └── plugins  
       ├── LobbyServerPlugin-1.0.0.jar   ← our plugin for this server
       ├── CleanroomGenerator-1.1.1.jar  
       └── Multiverse-Core-4.3.1.jar     
```

Compiling and getting the Jar for a given server is simple, but non-trivial, and there is a gradle task that will automatically build every plugin inside McPlugin and copy the resulting Jar to it's respective server folder (if there is one). This is the `gradle copyPluginJarToServer` task. The steps it performs are the following:

1. Build McPlugin and all gradle subprojects
2. Iterate over every subproject whose name ends with `ServerPlugin` (eg, _LobbyServerPlugin_, _GameServerPlugin_, ...). For each of these subprojects:
   1. Get the path of the built Jar file
   2. Find the server file that corresponds to this subproject
      - This is done by getting the base name of the subproject: everything before `ServerPlugin`. We expect there to be a folder under `minecraft-server/servers/{base name}/plugins/`.
   3. If the folder exists then take the built Jar and copy it inside the respective `plugins` folder.
      -  If there was already a file with the same name there then it will be overridden


### Naive hot-reload

The `copyPluginJarToServer` contains a small functionality that allows to have something akin to hot-reload when working on our servers (but not really). On the last step of this task, it will try to copy the jar of the plugin to `minecraft-server/servers/{base name}/plugins/`. If there was already a file with the same name there **AND** said file is in use (the Server process for that plugin has that file loaded) then the task won't be able to just replace it, and instead what it will do is enter a loop where it waits a while before retrying to replace the file again.

While this loop is running you can restart the server in question by opening the terminal where its process is running and typing:

```
reload confirm
```

This will cause the server to close the plugins file while reloading, which gives time to the loop mentioned above to replace the plugin file before the server actually reloads. This is a good way to introduce changes into an already running server without wasting time restarting the process from scratch.

> **NOTE:** when reloading a server you will stop it mid-execution so it might cause some inconsistencies in what the plugin is doing and its state. The plugin will restart from scratch after reload so this shouldn't be an issue, but if it's not working as expected then try stopping and restarting the server instead of reloading.

> **TIP:** you can run the `copyPluginJarToServer` for only one of the subprojects by specifying it's name before the task name itself. For example, to run this task only for `LobbyServer one would do:
> ```
> gradle Plugins:LobbyServerPlugin:copyPluginJarToServer
> ```