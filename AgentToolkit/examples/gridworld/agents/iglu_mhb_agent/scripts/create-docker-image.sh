#!/usr/bin/env bash

set -e

# Export variables with the names provided as arguments
# https://unix.stackexchange.com/a/353639/258777
for ARGUMENT in "$@"
do
    if [ "$ARGUMENT" == "--help" ]; then
        cat << EOF

USAGE:
    create-docker-image.sh BUILD_CONTEXT_PATH="context-path" POETRY_REPO_KEY="your-key" WANDB_TOKEN="your-key" IMAGE_NAME="greenlands/mhb-agent" IMAGE_TAG="0.0.1-GPU"

ARGS:
    NOTE: If the "required" ARGS are not provided then the script will ask them to you interactively.

    [required] BUILD_CONTEXT_PATH - Path that should be the context root of Docker's build process (in most cases this is the AgentToolkit folder)
    [required] POETRY_REPO_KEY - ADO Token used to authenticate with Greenlands private artifact repository
    [required] WANDB_TOKEN - W&B token used to download the weights (can be obtained from https://wandb.ai/authorize)

    [optional - Default: "greenlands/mhb-agent"] IMAGE_NAME - Name of the resulting Docker image
    [optional - Default: "0.0.1-GPU"] IMAGE_TAG - Tag of the resulting Docker image

This script will create a Docker image that runs the MHB agent as an Agent Toolkit agent.
The resulting image will have name \$IMAGE_NAME and tag \$IMAGE_TAG.

See "examples/agents/iglu_mhb_agent/README.md" for more information about the MHB Docker image.

EOF
        exit 1
    fi

    KEY=$(echo $ARGUMENT | cut -f1 -d=)

    KEY_LENGTH=${#KEY}
    VALUE="${ARGUMENT:$KEY_LENGTH+1}"

    export "$KEY"="$VALUE"
done

# set default values for the image name and tag
[ -z "$IMAGE_NAME" ] && IMAGE_NAME="greenlands/mhb-agent"
[ -z "$IMAGE_TAG" ] && IMAGE_TAG="0.0.1-GPU"

echo "Building Docker image $IMAGE_NAME:$IMAGE_TAG"

echo "Interactively asking for key values required for build (if necessary)"
if [ -z "$BUILD_CONTEXT_PATH" ]; then
    echo "BUILD_CONTEXT_PATH: " &&  read BUILD_CONTEXT_PATH
fi

# resolve abolute path to BUILD_CONTEXT_PATH
BUILD_CONTEXT_PATH=$(realpath $BUILD_CONTEXT_PATH)

if [ -z "$POETRY_REPO_KEY" ]; then
    echo "POETRY_REPO_KEY: " &&  read POETRY_REPO_KEY
fi

if [ -z "$WANDB_TOKEN" ]; then
    echo "WANDB_TOKEN: " &&  read WANDB_TOKEN
fi

# cd to folder where this script is located
cd "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")"

SCRIPT_DIR="$(pwd)"

echo ""
echo "----------------------------------------------"
echo "Building Docker image $IMAGE_NAME:$IMAGE_TAG"
echo ""
echo "BUILD_CONTEXT_PATH: $BUILD_CONTEXT_PATH"
echo "SCRIPT_DIR: $SCRIPT_DIR"
echo "POETRY_REPO_KEY: $POETRY_REPO_KEY"
echo "WANDB_TOKEN: $WANDB_TOKEN"
echo "----------------------------------------------"
echo ""

# cd to what will be the context root of the Docker build process
cd "$BUILD_CONTEXT_PATH"

# copy .dockerignore to root of context
cp "$SCRIPT_DIR/../.dockerignore" .

docker build \
    --file examples/agents/iglu_mhb_agent/Dockerfile \
    --tag "$IMAGE_NAME:$IMAGE_TAG" \
    --build-arg POETRY_REPO_KEY="$POETRY_REPO_KEY" \
    --build-arg WANDB_TOKEN="$WANDB_TOKEN" \
    .

# remove dockerignore from AT root
rm .dockerignore
