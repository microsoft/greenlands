# Convert env file to docker run command

Dashboard right now gives us the ability to copy all the required configuration
values required to run an agent as a `.env.local` file. This is very handy for
running the agent directly on the host machine. However, if we want to run the
agent in a docker container, we need to convert the `.env.local` file to a
docker run command.

This script does exactly that, it reads the `.env`-formatted values returned by
Dashboard and prints out a docker run command that will start an agent in docker
with the same configuration.

## How to run

First, go to Dashboard and open the agent you want to run in Docker. Then click
`Get Connection Keys` and finally, in the _AgentToolkit Environment Variables_
section, click `Copy!`. This will copy the `.env`-formatted values to your
clipboard.

Now create a file called `envfile` in this script's directory and paste the
`.env`-formatted values into it. Then run the script:

```bash
python convert_env_file_to_docker_command.py
```

The script will generate a docker run command for the default image (MHB agent).
If you want to use a different image, you can pass it as an argument:

```bash
python convert_env_file_to_docker_command.py --image <image name>
```

## Usage

The script provides a few other customization options that are specific to
running agents with Docker. You can see them with the `--help` flag:

```bash
usage: convert_env_file_to_docker_command.py [-h] [--log_level [{DEBUG,INFO,WARNING,ERROR}]] [--image IMAGE] [--gpu GPU]

Converts the .env.local contents copied from Dashboard to a docker run command

options:
  -h, --help            show this help message and exit
  --log_level [{DEBUG,INFO,WARNING,ERROR}]
                        Log level (default: INFO)
  --image IMAGE         Name of the image that you want to run
  --gpu GPU             GPU to use (default: 0)
```