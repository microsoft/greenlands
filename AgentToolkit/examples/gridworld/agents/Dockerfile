# Must use base image from MS approved list: https://eng.ms/docs/more/containers-secure-supply-chain/approved-images
FROM mcr.microsoft.com/mirror/docker/library/python:3.10-slim as base

# Keeps Python from generating .pyc files in the container
ENV PYTHONDONTWRITEBYTECODE 1
# Turns off buffering for easier container logging
ENV PYTHONUNBUFFERED 1
ENV POETRY_HOME=/opt/poetry

# Install and setup poetry
RUN pip install -U pipx \
  && apt-get update \
  # https://github.com/python-poetry/poetry/issues/5977#issuecomment-1262365306
  && pipx install poetry==1.2.0

ENV PATH="${PATH}:/root/.poetry/bin:/root/.local/bin"

FROM base as deps

ARG AGENT_NAME

WORKDIR /usr/src/app

COPY ./examples/agents/${AGENT_NAME}/pyproject.toml ./examples/agents/${AGENT_NAME}/poetry.lock ./examples/agents/${AGENT_NAME}/

ARG PACKAGE_FEED_NAME="deeplearningeng"
ARG PACKAGE_FEED_TOKEN

ENV POETRY_HTTP_BASIC_PLAIGROUND_USERNAME="${PACKAGE_FEED_NAME}" \
  POETRY_HTTP_BASIC_PLAIGROUND_PASSWORD="${PACKAGE_FEED_TOKEN}"

WORKDIR /usr/src/app/examples/agents/${AGENT_NAME}

RUN poetry config virtualenvs.create false \
  && poetry install --no-interaction --no-ansi

FROM deps as agent

WORKDIR /usr/src/app

COPY . .

# Set ARG as environment variable to allow substituting AGENT_NAME in the Entrypoint shell command
# https://docs.docker.com/engine/reference/builder/#understand-how-arg-and-from-interact
ARG AGENT_NAME
ENV AGENT_NAME=${AGENT_NAME}
# We need to set the PYTHON path to allow the module we are executing to access resources outside of the module such as common/action_space
ENV PYTHONPATH=/usr/src/app:$PYTHONPATH

# In order for environment variable substitution to occur we must leverage the shell
# From the docs, "ENTRYPOINT [ "echo", "$HOME" ] will not do variable substitution on $HOME"
# https://docs.docker.com/engine/reference/builder/#entrypoint
ENTRYPOINT poetry run python /usr/src/app/examples/agents/${AGENT_NAME}/run_agent_service.py