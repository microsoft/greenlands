# Sample Environments

In this directory you will find some simple environments intended to show how to extend the basic components, `GameEnvironment` and `Agent` to develop a new agent.

It also includes scripts to run the agent locally with a simulated server and client based running on threads, or with the Plaiground Minecraft Server.

**TODO** Add clarification about how to run the agents, and possibly add parameters to the scripts to
specify which agent to run, and to the agent to specify which environment they should be run in.

## Command Agent

The command agent is a simple heuristic model that predicts actions based on
chat commands received from the server.

Currently, the supported commands are:

    repeat <N> <command>: Executes the command N times. Example: repeat 10 "move:right"
    echo:<message_to_echo>: Returns a chat event with message "Hi! You said <message_to_echo>".
    move:<[up|down|north|south|east|west]>: alters x, y, z coordinates in the desired direction.
        The value of the increase/decrease is determined by `self.position_movement_distance`.
        To move east/west increase or decrease the x coordinate respectively.
        To move south/north increase or decrease the z coordinate respectively.
        To move up/down increase or decrease the y coordinate respectively.
        See https://minecraft.fandom.com/wiki/Coordinates#World_coordinates
    look:<[up|down]> moves pitch by 45 degrees on the desired direction.
        Looking stratight up corresponds to pitch -90, and straigh down to pitch 90.


### Local game simulator

In this example, server and agent will exchange the events in the following way. The order of some of the events can vary, with the exacption of confirmation events and agent action events triggered by server instructions. Note that some of the commands are outdated in the diagram, but the exchange of messages follows the same logic.

[![](https://mermaid.ink/img/pako:eNqtVd9P2zAQ_ldufqJSqLTXSEWKtmoaYlNF8xip8uxLa-Efme2UZYj_fecYtAJBQMEvOdvffff5fL7cMOEkspIF_N2jFfhV8a3nprFAo-M-KqE6biOsgAe4cIJrWKPfo38KqeqEqbZIdu2cvlKxsRmmnevudlQAj1wOEB10mg-QEVV9ena2KjPoe7hMkOWe7BOeVjaBgiqBGyUXwhnDrTwdNwrwTo_Lv3qlJfpZ5vvpIoIjncRcEO83bhBCJLkhA1YUr6pLWGkeW-dNAqzTfo66pWli7XslP8-mXeg7oD93yobkPeH4nDouotpzUljV76d2cYd-040eT5XS4pcdj5MMBkOgLC4aJgjSsGO9DSX6eG-N_MD98cXlqsk3BwGtVHYLmBhDAWEsRSqo0DkrA1yruAPhbKu84VE5-7C4XpD06K4ONV6OEQJC650BSnjW9Qnm8_lbT_7eMA9P84Ny9bowFq83ml5wysvi5E8BQwF_C-jImE2e4EOpD1QfVPhFuvu3vp7pEhnLKEBiyDCJr3xmHyIid0XAVIb_NRwTcDreVL9aWvl8tyIl2bgfrGAG6V0oSQ3_Ju01jEqMpLKSTIkt7zV1gcbeErTvJGVuKVV0npXR91gw3ke3Hqy4n2fM3T-DlS3XAW__AWssJDA)](https://mermaid.live/edit#pako:eNqtVd9P2zAQ_ldufqJSqLTXSEWKtmoaYlNF8xip8uxLa-Efme2UZYj_fecYtAJBQMEvOdvffff5fL7cMOEkspIF_N2jFfhV8a3nprFAo-M-KqE6biOsgAe4cIJrWKPfo38KqeqEqbZIdu2cvlKxsRmmnevudlQAj1wOEB10mg-QEVV9ena2KjPoe7hMkOWe7BOeVjaBgiqBGyUXwhnDrTwdNwrwTo_Lv3qlJfpZ5vvpIoIjncRcEO83bhBCJLkhA1YUr6pLWGkeW-dNAqzTfo66pWli7XslP8-mXeg7oD93yobkPeH4nDouotpzUljV76d2cYd-040eT5XS4pcdj5MMBkOgLC4aJgjSsGO9DSX6eG-N_MD98cXlqsk3BwGtVHYLmBhDAWEsRSqo0DkrA1yruAPhbKu84VE5-7C4XpD06K4ONV6OEQJC650BSnjW9Qnm8_lbT_7eMA9P84Ny9bowFq83ml5wysvi5E8BQwF_C-jImE2e4EOpD1QfVPhFuvu3vp7pEhnLKEBiyDCJr3xmHyIid0XAVIb_NRwTcDreVL9aWvl8tyIl2bgfrGAG6V0oSQ3_Ju01jEqMpLKSTIkt7zV1gcbeErTvJGVuKVV0npXR91gw3ke3Hqy4n2fM3T-DlS3XAW__AWssJDA)

