# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.245.2/containers/python-3/.devcontainer/base.Dockerfile

# [Choice] Python version (use -bullseye variants on local arm64/Apple Silicon): 3, 3.10, 3.9, 3.8, 3.7, 3.6, 3-bullseye, 3.10-bullseye, 3.9-bullseye, 3.8-bullseye, 3.7-bullseye, 3.6-bullseye, 3-buster, 3.10-buster, 3.9-buster, 3.8-buster, 3.7-buster, 3.6-buster
ARG VARIANT="3.10-bullseye"
FROM mcr.microsoft.com/vscode/devcontainers/python:0-${VARIANT}

# [Choice] Node.js version: none, lts/*, 16, 14, 12, 10
ARG NODE_VERSION="none"
RUN if [ "${NODE_VERSION}" != "none" ]; then su vscode -c "umask 0002 && . /usr/local/share/nvm/nvm.sh && nvm install ${NODE_VERSION} 2>&1"; fi


# add poetry and just
RUN su vscode -c "curl -sSL https://install.python-poetry.org | python - --version 1.2.1"

# tell poetry to create the virtualenvs inside .venv directories in the project.
# This ensures VScode is able to find all dependencies and properly suggest
# completions and types.
RUN su vscode -c "~/.local/bin/poetry config virtualenvs.in-project true"

RUN su vscode -c "cd /tmp && \
                  wget -O just.tar.gz https://github.com/casey/just/releases/download/1.5.0/just-1.5.0-aarch64-unknown-linux-musl.tar.gz && \
                  tar xvf just.tar.gz && \
                  cp just ~/.local/bin && \
                  ~/.local/bin/just --completions bash >> ~/.bashrc"

# disable zsh git info to speed up shell                  
RUN su vscode -c "git config --global codespaces-theme.hide-status 1"

RUN apt update 

# dependencies to enable GPG signing and Git LFS support
RUN apt install -yq gnupg2 git-lfs

RUN su vscode -c "git lfs install"

# install webgl to allow rendering with gridworld command agent
RUN apt install -yq \
                    and \
                    freeglut3-dev \
                    libegl1 \
                    libegl1-mesa-dev \
                    libgl-dev \
                    libgl1 \
                    libgl1-mesa-dev \
                    libgl1-mesa-glx \
                    libgles2 \
                    libgles2-mesa-dev \
                    libglvnd-dev \
                    libglvnd0 \
                    libglx0 \
                    libx11-6 \
                    libxau6 \
                    libxcb1 \
                    libxdmcp6 \
                    libxext6 \
                    mesa-utils \
                    pkg-config \
                    xvfb 

# faster text search
RUN apt install -yq ripgrep