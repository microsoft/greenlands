FROM mcr.microsoft.com/devcontainers/base:ubuntu-22.04

SHELL ["/bin/bash", "-c"]


#################################################
# base dependencies and configurations

# disable zsh git info to speed up shell                  
RUN su vscode -c "git config --global codespaces-theme.hide-status 1"
RUN apt update 

RUN apt install -yq gnupg2 git-lfs ripgrep unzip zip

# enable git lfs
RUN su vscode -c "git lfs install"

# install Azure Cli
RUN curl -sL https://aka.ms/InstallAzureCLIDeb | bash

# install powershell
RUN wget "https://github.com/PowerShell/PowerShell/releases/download/v7.3.0/powershell_7.3.0-1.deb_amd64.deb" && \
    dpkg -i powershell_7.3.0-1.deb_amd64.deb && \
    rm powershell_7.3.0-1.deb_amd64.deb


#################################################
# install Agent Toolkit dependencies

#* Python 3.10.6 is already installed in base image, so there's no need to
#* install it separately. However, the command is `python3`, so here we do
#* create an alias for easier access
RUN su vscode -c "echo 'alias python=\"python3\"' >> ~/.bashrc"

# add poetry and just
RUN su vscode -c "curl -sSL https://install.python-poetry.org | python3 - --version 1.2.1"

# tell poetry to create the virtualenvs inside .venv directories in the project.
# This ensures VScode is able to find all dependencies and properly suggest
# completions and types.
RUN su vscode -c "~/.local/bin/poetry config virtualenvs.in-project true"

RUN su vscode -c "cd /tmp && \
                  wget -O just.tar.gz https://github.com/casey/just/releases/download/1.5.0/just-1.5.0-aarch64-unknown-linux-musl.tar.gz && \
                  tar xvf just.tar.gz && \
                  cp just ~/.local/bin && \
                  ~/.local/bin/just --completions bash >> ~/.bashrc"


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


#################################################
# install Plugin dependencies

RUN su vscode -c "curl -s 'https://get.sdkman.io' | bash && \
    source '/home/vscode/.sdkman/bin/sdkman-init.sh' && \
    sdk install java 17.0.4-ms && \
    sdk install gradle 7.6"

RUN curl -Lo kubectl https://dl.k8s.io/release/v1.26.0/bin/linux/amd64/kubectl && \
    chmod a+x ./kubectl && \
    mv ./kubectl /usr/local/bin

RUN curl -Lo skaffold https://github.com/GoogleContainerTools/skaffold/releases/download/v1.39.2/skaffold-linux-amd64 && \
    chmod a+x ./skaffold && \
    mv ./skaffold /usr/local/bin

RUN curl -Lo terraform https://releases.hashicorp.com/terraform/1.3.6/terraform_1.3.6_darwin_amd64.zip && \
    chmod a+x ./terraform && \
    mv ./terraform /usr/local/bin
