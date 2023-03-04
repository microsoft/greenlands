# Gridworld Command Agent example

This is an example of an agent that uses the AT but runs it's own local
simulation in Gridworld. Gridworld acts as the source of truth for block and
player locations. Only chat events coming from PlaiGround are included in the
local game state.

## Running with a renderer

Gridworld has its own renderer that can be used to debug the state of the world.
By default, this is disabled.

### Extra steps to work on Windows

The renderer itself only works on Linux. If you're on Windows then the
recommended way to get it to work is for you to use VSCode and open the root
PlaiGround project in the `.devcontainer`. For more info about dev containers
and how to use it, refer to [the
documentation](https://code.visualstudio.com/docs/remote/containers).

Devcontainers can be [slightly slow on
Windows](https://code.visualstudio.com/remote/advancedcontainers/improve-performance).
If you experience this, then the recommended way to speed it up is to clone the
PlaiGround repo in WSL and THEN open the repo in the devcontainer.

Note that if you use devcontainer then you need to remember to tell _Poetry_
what token to use to authenticate with out private ADO artifact repo:

```bash
poetry config http-basic.PlaiGround deeplearningeng <token>
```

Once that's done, you'll need to install an x-server on Windows so that the
openGL context can be shown. The recommended way is to install
[VcXsrv](https://sourceforge.net/projects/vcxsrv/) using
[Scoop](https://github.com/ScoopInstaller/Scoop):

```powershell
# enable the `extras` bucket if you haven't done so already
scoop bucket add extras

# install vcxsrv
scoop install vcxsrv
```

Once the installation is finished, look for `XLauncher` in your Windows start
menu, select:

- multiple windows
- change _display number_ to `0`
- next
- start no client
- next
- next
- finish

### Setting up dependencies

The dependencies needed to run the renderer are marked as _optional_ and poetry
does not install them by default. To install them do:

```powershell
# from the gridworld_command folder
poetry install --with render
```

### Set `render` property to `True`

Open the file you want to run (`run_local_agent.py` or `run_agent_service.py`)
and find the line where `GridWorldGameEnvironment` is initialized. Change the
`render` parameter from `False` to `True`.

### Selecting Interpereter

You'll need to run the agent using the appropriate Poetry environment. The
easiest way is to do so with VSCode. First ensure that the selected interpreter
is the one for the `gridworld-command-agent` environment.

If you're not sure then open the VSCode command pannel, search for `select interpreter` and select
the appropriate interpreter from the list.

If VSCode does not auto-detect the interpreter, you can navigate to the `gridworld_command` folder and run `poetry env list --full-path`

Inside the devcontainer, this will output a path such as:

```bash
/home/vscode/.cache/pypoetry/virtualenvs/gridworld-command-agent-dnoXMMAO-py3.10 (Activated)
```

Work directly on Windows machine, this will output a path such as:

```powershell
C:\Users\mattm\AppData\Local\pypoetry\Cache\virtualenvs\gridworld-command-agent-DOExUNHW-py3.10 (Activated)
```

Then you can manually enter this path when selecting an interpreter.

```bash
/home/vscode/.cache/pypoetry/virtualenvs/gridworld-command-agent-dnoXMMAO-py3.10/bin/python
```

### Setting IGLU_HEADLESS to 0

Now go to the `.vscode/launch.json` file and change the `IGLU_HEADLESS`
environment variable of the configuration you want to use from `1` to`0`.

If you don't see this you will see an empty Pyglet window while the game runs but nothing
will be displayed on it.

## Run

Once that's done, open VSCode's `Run and Debug` panel in the sidebar, and at the
top select the `GridworldCommand` configuration you want (_local_ or _remote_).
