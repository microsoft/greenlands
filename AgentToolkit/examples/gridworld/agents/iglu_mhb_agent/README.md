# MHB Agent

This readme explains how to run the [MHB baseline
agent](https://gitlab.aicrowd.com/aicrowd/challenges/iglu-challenge-2022/iglu-2022-rl-mhb-baseline/-/tree/master)
so that it works properly with Greenland and the rest of the Agent Toolkit.

For the MHB to work it needs to have a renderer available (since one of the
inputs of the model is the RGB matrix of the current frame). To enable renderer
output please check out the relevant section in the [gridworld_command agent
README](../gridworld_command/README.md)

## Set up

Install all python dependencies using poetry

```bash
$ poetry install --with render --with linuxdeps
```

Now we'll clone the [IGLU 22 RL
baseline](https://gitlab.aicrowd.com/aicrowd/challenges/iglu-challenge-2022/iglu-2022-rl-mhb-baseline).
But before doing that ensure that [Git LFS](https://git-lfs.github.com/) is
installed locally (if you're using the devcontainer then this was already done
for you when the container was created). The current installation instructions
are:

```bash
# note: the repo has some very large files so the cloning process might take a while
git clone http://gitlab.aicrowd.com/aicrowd/challenges/iglu-challenge-2022/iglu-2022-rl-mhb-baseline.git iglu_2022_mhb_baseline

cd iglu_2022_mhb_baseline
git reset --hard 253ae17ee6ee4daa3a9d02da56b6a7bdad7c77d8

# Now we need to apply a patch file to the repo to fix some issues with the base code,
# as well as correcting where the configuration files are found.
# Check out the patch files for more information
git apply ../patches/fix_mhb_paths.patch

# [OPTIONAL] if you want to run the model on CPU then also apply the following
git apply ../patches/run_mhb_on_cpu.patch

cd ..
```

To activate or enter the poetry environment, run:

```bash
$ poetry shell
```


## Download weights

Before actually being able to run the MHB agent you'll need to download the
model's weights, which are hosted on [Weights & Biases](https://wandb.ai/). The
MHB repo contains files that will download the weights for you, but you first
need to authenticate with your local _wandb_. Create an account and then copy
your token (or go [here](https://wandb.ai/authorize), where you can copy your
token as well). Then run the following and follow the on-screen instructions
(ensure you're inside your poetry shell that we created in the previous step
with all the dependencies)

```bash
wandb login
```

Once you've authenticated with _wandb_ you can download the weights by running
the following:

```bash
# Download weights for the NLP model (~2.8GB)
cd iglu_2022_mhb_baseline/agents/mhb_baseline/nlp_model/
python download.py

# Download weights for RL model. This is automatically done for us by the local_evaluation script
# go back up to the iglu_2022_mhb_baseline folder
cd ../../../

# call the local_evaluation script. Note that as soon as you start seeing messages like:
#   Num Steps: 500, Num episodes: 7
# it means that the weights were already downloaded and you can Ctrl+C to stop the process
python local_evaluation.py
```


## Running the agent

The _executable_ files in this example are:

- `run_local_agent_service.py` - Runs the MHB agent using a dummy message queue
- `run_agent_service.py` - Runs the MHB agent using event hub for communication
- `iglu_local_evaluation.py` - Evaluation file provided that has been slightly
  adapted from the MHB repo, but runs the agent inside our
  GridworldGameEnviroment instead of the basic Gridworld. Mainly used to check
  that things as working as expected.

The recommended way to run the agent is by using VSCode's `Run and Debug`
feature. But if you like to live in your terminal then you can also execute them
directly from there by first ensureing you're in the MHB Agent shell created in
the step above, and then, from the `AgentToolkit` folder, running:

```bash
cd AgentToolkit

export PYTHONPATH=$(pwd)

# NOTE: if you're in a headless environment then you can prepend `xvfb-run` to the
# following commands to have the Gridworld renderer render to a virtual display.

# run_local_agent_service
python examples/agents/iglu_mhb_agent/run_local_agent_service.py

# run_agent_service
python examples/agents/iglu_mhb_agent/run_agent_service.py

# iglu_local_evaluation
python examples/agents/iglu_mhb_agent/iglu_local_evaluation.py


# note, if you also want to enable the renderer output for any of these then you'll need to prepend
# IGLU_HEADLESS=0, for example:
#   IGLU_HEADLESS=0 PYTHONPATH=$(pwd) python examples/agents/iglu_mhb_agent/iglu_local_evaluation.py
```

### Running headlessly with persistent virtual display

As mentioned above, an option to run headlessly is to append `xvfb-run` to the
commands. If you don't want to do this or you want to, for instance, debug with
VSCode in a headless environment then you can start up a persistent virtual
display and set the `DISPLAY` env variable to the appropriate ID so that Pyglet
can properly connect to it.

```bash
# use a dedicated terminal to start virtual display #99 (or whatever other number you prefer)
Xvfb :99

# now set the DISPLAY env variable wherever it is you're going to run the main python process
export DISPLAY=":99"
python ...
```


# Docker

It is possible to run the agent as a Docker container. The following sections
explain how to do so as well as how to build the image locally.

## Building Docker image

From the `examples/agents/iglu_mhb_agent` folder you can run the following
command to build the docker image for the IHB agent locally.

```bash
# BUILD_CONTEXT_PATH - Path that should be the context root of Docker's build process (in most cases this is the AgentToolkit folder)
# POETRY_REPO_KEY    - is the ADO token used to authenticate with our private artifact
#                      repository
# WANDB_TOKEN        - is your personal WANDB token, which you can get from here:
#                      https://wandb.ai/authorize

./scripts/create-docker-image.sh \
              BUILD_CONTEXT_PATH="../../.." \
              POETRY_REPO_KEY="your-key" \
              WANDB_TOKEN="your-key"

# run `./scripts/create-docker-image.sh --help` for more info on this script
```

## Running local Docker image

```bash
docker run -it --gpus all \
               --env LOGLEVEL="DEBUG" \
               --env AGENT_SERVICE_ID="xxx" \
               --env AGENT_SERVICE_ROLE_ID="xxx" \
               --env PUBLISH_SUBSCRIBE_CONNECTION_STRING="xxx" \
               --env EVENT_HUB_CONSUMER_GROUP="xxx" \
               greenland/mhb-agent:0.0.1-GPU
```
