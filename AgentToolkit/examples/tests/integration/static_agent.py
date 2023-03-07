from agent_toolkit import Agent


class StaticAgent(Agent):
    def __init__(self, agent_service_id):
        super().__init__(agent_service_id)

        self.action_map = {k: idx for (idx, k) in enumerate([
            "noop", "forward", "back", "left", "right", "jump", "hotbar-1", "hotbar-2", "hotbar-3",
            "hotbar-4", "hotbar-5", "hotbar-6", "camera left", "camera right", "camera up",
            "camera down",
            "attack", "use", "end_turn"
        ])}

        self.remaining_actions = [1, 2, 3, 4]
        self.action_count = 0
        self.action_repeat_times = 5

    def next_action(self, observation, current_game_state) -> int:
        if len(self.remaining_actions) == 0:
            self.remaining_actions = [1, 2, 3, 4]
            self.action_count = 0
            return self.action_map["end_turn"]

        current_action = self.remaining_actions[0]

        self.action_count += 1

        # action_count is the amount of times the current action will be returned
        if self.action_count == self.action_repeat_times:
            self.action_count = 0
            self.remaining_actions.pop(0)

        return current_action
