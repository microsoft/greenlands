import os


def get_env_var(var_name):
    value = os.getenv(var_name)

    assert value, f"{var_name} is not set! It must be a non-empty string"

    return value
