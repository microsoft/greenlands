#!/usr/bin/env bash

set -e

cd /app

export PYTHONPATH=$(pwd)

xvfb-run python examples/agents/iglu_mhb_agent/run_agent_service.py
