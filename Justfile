help:
    @just --list

start-bore:
    # cargo install bore-cli
    bore local 25565 --to bore.pub

start-redis:
    docker run --rm -it --name greenlands-redis -p 6379:6379 -d redis

run-proxy:
    cd /workspaces/greenlands/McPlugin/minecraft-server/proxies/gameProxy && \
    pwsh start.ps1

run-lobby:
    cd /workspaces/greenlands/McPlugin/minecraft-server/servers/LobbyServer && \
    pwsh start.ps1

run-game:
    cd /workspaces/greenlands/McPlugin/minecraft-server/servers/GameServer-1 && \
    pwsh debug.ps1

run-service:
    cd /workspaces/greenlands/Service/Greenlands.Api && \
    dotnet run

run-dashboard:
    cd /workspaces/greenlands/Dashboard && \
    npm run dev

start-vdisplay:
    Xvfb :99

run-felipe:
    cd /workspaces/greenlands/AgentToolkit/examples/agents/iglu_felipe_agent && \
    source ./.venv/bin/activate && \
    export DISPLAY=:99 && \
    PYTHONPATH=/workspaces/greenlands/AgentToolkit IGLU_HEADLESS=1 LOGCOLOR=TRUE LOGLEVEL=DEBUG \
    python run_agent_service.py

install-deps-for-agent:
    cd /workspaces/greenlands/AgentToolkit/examples/agents/iglu_felipe_agent && \
    source ./.venv/bin/activate && \
    cd /workspaces/greenlands/AgentToolkit && \
    poetry install && \
    cd /workspaces/greenlands/AgentToolkit/examples/agents/iglu_felipe_agent && \
    poetry install --with render --with linuxdeps && \
    pip install azure-storage-blob wandb