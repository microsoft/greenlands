# Static agent

This agent will send static actions via AT where the sent events and received events are
recorded. If the `--compare true` option is passed as an argument then, when the 
PlatformGameEndEvent is received the positions in the move events that were sent are compared
to the positions in the received move events to make sure that they are the same. If there are 
events where the positions are not the same, those events are written to a json file (e.g. compare_
output_*.json).

When the PlatformGameEndEvent is 
received, all the sent and received events are written to a json file (e.g. sent_recieved_events_*.json).
This file can then be passed to the `compare_events.py` for the comparision to be done.

Once this agent is running and you have joined it in a game via the Minecraft client, send `/end-turn` in
the chat. This will prompt the agent to perform all of its static actions. When it is the humans turn send
`/finish_game success` in the chat. This will end the game and the agent will either compare the events
or will write the events to a file.


### Setting up dependencies

To install them do:

```powershell
# from the AgentToolkit folder
poetry install
```

## Run

```powershell
# from the AgentToolkit folder
poetry shell
poetry run python examples\tests\integration\run_static_agent_service.py --compare true
```

## Compare sent and received events

```powershell
# from the AgentToolkit folder
poetry shell
poetry run python examples\tests\integration\compare_events.py --filepath sent_received_events_10-20-22_10-35-10.json
```
## Sequence Diagram

[![](https://mermaid.ink/img/pako:eNqlkl9rgzAUxb9KuE8bc6JVq-ahULrCXgqD7mkIJY13bbaYdBqlf-h3X9S1rNDtYcvT5eR3TpKbewCucwQKFX7UqDg-CLYqWZEpYtd4hcrcj0Z3XfGstXwXhpIxN0IrUugGb7YO2Tlkf_vN8MXdW-NECisQoQyWiknCGV8jJdOmVS_8pLLSlRB7eEc_1survt5yQlp8NiFPsl4J9Qt_Zv6Qf9mLHy3_aEWJHEWDeR901dgmth1bnNgFtkGV-1Zp-_A5a5AwKUmvggMFlgUTuf3pQxubgVljgRlQWy5ZZatMHS3HaqPnO8WBmrJGB-pNzsxpKoC-Mlmd1WkujC7P4oYpoAfYAg1T14-DOA0TLxkMgyCNHNgB9X3fDfw08pM49oaeF4dHB_Za21zPTQdeEA3CKEmjIInisMt76Tb7m2B32qwf125qj586E-K0?type=svg)](https://mermaid-js.github.io/mermaid-live-editor/edit#pako:eNqlkl9rgzAUxb9KuE8bc6JVq-ahULrCXgqD7mkIJY13bbaYdBqlf-h3X9S1rNDtYcvT5eR3TpKbewCucwQKFX7UqDg-CLYqWZEpYtd4hcrcj0Z3XfGstXwXhpIxN0IrUugGb7YO2Tlkf_vN8MXdW-NECisQoQyWiknCGV8jJdOmVS_8pLLSlRB7eEc_1survt5yQlp8NiFPsl4J9Qt_Zv6Qf9mLHy3_aEWJHEWDeR901dgmth1bnNgFtkGV-1Zp-_A5a5AwKUmvggMFlgUTuf3pQxubgVljgRlQWy5ZZatMHS3HaqPnO8WBmrJGB-pNzsxpKoC-Mlmd1WkujC7P4oYpoAfYAg1T14-DOA0TLxkMgyCNHNgB9X3fDfw08pM49oaeF4dHB_Za21zPTQdeEA3CKEmjIInisMt76Tb7m2B32qwf125qj586E-K0)
