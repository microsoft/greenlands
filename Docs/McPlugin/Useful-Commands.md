# Useful Commands during Development

## Viewing Dependency Graph of Gradle Project

You may use the gradle command to view the dependency graph of certain projects.

```powershell
gradle Plugins:LobbyServerPlugin:dependencies --configuration compileClasspath
```

Note: The name of the server `LobbyServerPlugin` in the command. This will only show the dependence of the LobbyServerPlugin.
