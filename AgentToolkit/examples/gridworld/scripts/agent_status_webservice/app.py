import json
import os
from typing import List
from flask import Flask, request
import subprocess
import dotenv

dotenv.load_dotenv()

NEEDS_SUDO: bool = os.getenv("NEEDS_SUDO", "false") == "true"
API_KEY = os.getenv("API_KEY", "")

if API_KEY == "":
    raise AssertionError("API_KEY environment variable needs to be set!")

app = Flask(__name__)


def _execute_commands_and_return_stdout(commands: List[str]) -> str:
    result = subprocess.run(commands, stdout=subprocess.PIPE)
    return result.stdout.decode("utf8")


def _run_docker_command(commands: List[str]):
    command_tokens = []

    if NEEDS_SUDO:
        command_tokens.append("sudo")

    command_tokens.append("docker")
    command_tokens += commands
    return _execute_commands_and_return_stdout(command_tokens)


def _get_container_inspected_data(container_ids: List[str]) -> str:
    formatter = """
    {
        "Id": "{{.Id}}",
        "Name": "{{.Name}}",
        "Image": "{{.Config.Image}}",
        "Running": {{.State.Running}},
        "Envs": {{json .Config.Env}}
    }
    """.replace("\n", "")

    results = _run_docker_command(
        ['container', 'inspect'] + container_ids + ['--format='+formatter]
    ).split("\n")

    def get_env_value(envs: List[str], key: str):
        for e in envs:
            if e.startswith(key):
                return e[e.find("=")+1:]
        return None

    container_data = []
    for r in results:
        if r == "":
            continue

        container_json = json.loads(r)

        container_data.append({
            "id": container_json["Id"],
            "name": container_json["Name"],
            "image": container_json["Image"],
            "running": container_json["Running"],
            "agent_id": get_env_value(container_json["Envs"], "AGENT_SERVICE_ID"),
            "role_id": get_env_value(container_json["Envs"], "AGENT_SERVICE_ROLE_ID"),
            "consumer_group": get_env_value(container_json["Envs"], "EVENT_HUB_CONSUMER_GROUP"),
        })

    return container_data


@app.route("/agents/<agent_id>/status")
def is_agent_running(agent_id: str):
    if request.headers.get("X-API-KEY", "") != API_KEY:
        return {"status": "Forbidden", "message": "API Key is invalid"}, 403

    container_ids = [
        s
        for s in _run_docker_command(['container', 'ls', '--format={{.ID}}']).split("\n")
        if s != ''
    ]

    containers_data = _get_container_inspected_data(
        container_ids=container_ids)

    for data in containers_data:
        if data["agent_id"] == agent_id:
            return {"status": "Found", "data": data}, 200

    return {"status": "Not found"}, 404


@app.route("/")
def home():
    return "Greenlands Agent Container Status Reporter"
