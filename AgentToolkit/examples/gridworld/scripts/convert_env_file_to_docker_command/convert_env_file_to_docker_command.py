import argparse
import os
import dotenv


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Converts the .env.local contents copied from Dashboard to a docker run command",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument("--log_level",
                        default="INFO",
                        const='INFO',
                        nargs='?',
                        choices=['DEBUG', 'INFO', 'WARNING', 'ERROR'],
                        help="Log level")

    parser.add_argument("--image",
                        help="Name of the image that you want to run",
                        default="plaiground4bawacr.azurecr.io/plaiground/mhb-agent:0.0.1-GPU")

    parser.add_argument("--gpu",
                        default="0",
                        help="GPU to use")

    parser.add_argument("--name",
                        default=None,
                        help="Name of the running container")

    parser.add_argument("--consumer_group",
                        default=None,
                        help="Consumer group, only used if ENV consumer group is empty")

    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()

    dotenv.load_dotenv("envfile")

    agent_service_id = os.getenv("AGENT_SERVICE_ID")
    agent_service_role_id = os.getenv("AGENT_SERVICE_ROLE_ID")
    publish_subscribe_connection_string = os.getenv("PUBLISH_SUBSCRIBE_CONNECTION_STRING")
    taskdata_container_url = os.getenv("TASKDATA_CONTAINER_URL")
    event_hub_consumer_group = os.getenv("EVENT_HUB_CONSUMER_GROUP")

    if event_hub_consumer_group == "":
        event_hub_consumer_group = args.consumer_group

    name_flag = ""
    if args.name is not None:
        name_flag = f'--name {args.name}'

    print(f'docker run --rm -it {name_flag} --gpus \'"device={args.gpu}"\' '
          f'--env LOGLEVEL="{args.log_level}" '
          f'--env AGENT_SERVICE_ID="{agent_service_id}" '
          f'--env AGENT_SERVICE_ROLE_ID="{agent_service_role_id}" '
          f'--env PUBLISH_SUBSCRIBE_CONNECTION_STRING="{publish_subscribe_connection_string}" '
          f'--env TASKDATA_CONTAINER_URL="{taskdata_container_url}" '
          f'--env EVENT_HUB_CONSUMER_GROUP="{event_hub_consumer_group}" '
          f'{args.image}')
