FROM python:3.10-bullseye AS base

SHELL ["/bin/bash", "-c"]

# used for installing dependencies
ARG POETRY_REPO_KEY

RUN curl -sSL https://install.python-poetry.org | python - --version 1.2.1
ENV PATH="${PATH}:~/.local/bin/"

RUN poetry config http-basic.Greenlands deeplearningeng $POETRY_REPO_KEY

RUN apt update && \
        apt install -yq gnupg2 git-lfs && \
        git lfs install

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


###################################################################################

FROM base AS mhb_repo_setup

# used for downloading weights
ARG WANDB_TOKEN

WORKDIR /tmp

# clone mhb repo
RUN git clone http://gitlab.aicrowd.com/aicrowd/challenges/iglu-challenge-2022/iglu-2022-rl-mhb-baseline.git iglu_2022_mhb_baseline && \
    cd iglu_2022_mhb_baseline && \
    git reset --hard 253ae17ee6ee4daa3a9d02da56b6a7bdad7c77d8

WORKDIR /tmp/iglu_2022_mhb_baseline

COPY examples/agents/iglu_mhb_agent/patches /tmp/patches/

# apply patches
RUN git apply /tmp/patches/fix_mhb_paths.patch && \
    git apply /tmp/patches/local_evaluation_does_1_task.patch && \
    git apply /tmp/patches/fix_requirements_to_download_weights.patch

# TODO: could add optional step to create CPU version of image

RUN pip install -r requirements.txt && \
    pip install git+https://github.com/iglu-contest/gridworld.git@master

# to download the weights we to be able to run in CPU so we apply the
# appropriate patch and after weights are downloaded we "unapply" it.
RUN git apply /tmp/patches/run_mhb_on_cpu.patch && \
    wandb login $WANDB_TOKEN && \
    cd agents/mhb_baseline/nlp_model/ && \
    python download.py && \
    cd ../../../  && \
    python local_evaluation.py && \
    git apply -R /tmp/patches/run_mhb_on_cpu.patch

###################################################################################

FROM base AS final

WORKDIR /app

ADD . .

COPY --from=mhb_repo_setup /tmp/iglu_2022_mhb_baseline /app/examples/agents/iglu_mhb_agent/iglu_2022_mhb_baseline

WORKDIR /app/examples/agents/iglu_mhb_agent

# install mhb agent dependencies
RUN poetry lock --no-update && \
    poetry install --with render --with linuxdeps && \
    poetry run pip install git+https://github.com/iglu-contest/gridworld.git@master

# used at runtime to tell the agent information about `who` it should be playing
# as
ENV LOGLEVEL "INFO"
ENV AGENT_SERVICE_ID ""
ENV AGENT_SERVICE_ROLE_ID ""
ENV TASKDATA_CONTAINER_URL ""
ENV EVENT_HUB_CONSUMER_GROUP ""
ENV PUBLISH_SUBSCRIBE_CONNECTION_STRING ""

ENTRYPOINT poetry run bash scripts/docker-entrypoint.sh
