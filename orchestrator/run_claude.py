#!/usr/bin/env python3
"""Claude Code CLI wrapper — runs claude with given prompt and parses output."""

import json
import os
import subprocess
import sys
import tempfile


def run_claude(prompt, workdir=None, max_turns=10):
    """
    Run Claude Code CLI non-interactively.

    Args:
        prompt: The task prompt.
        workdir: Working directory for Claude. Uses a temp dir if None.
        max_turns: Maximum agent turns.

    Returns:
        dict with keys: success, result, session_id, cost_usd
    """
    if workdir is None:
        workdir = tempfile.mkdtemp(prefix="claude_workdir_")

    os.makedirs(workdir, exist_ok=True)

    cmd = [
        "claude",
        "--dangerously-skip-permissions",
        "-p", prompt,
        "--max-turns", str(max_turns),
        "--output-format", "json",
        "--workdir", workdir,
    ]

    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=600,
            env=os.environ.copy(),
        )

        if proc.returncode != 0:
            return {
                "success": False,
                "result": proc.stderr.strip() or "claude exited non-zero",
                "session_id": None,
                "cost_usd": None,
            }

        try:
            data = json.loads(proc.stdout)
        except json.JSONDecodeError:
            return {
                "success": True,
                "result": proc.stdout.strip(),
                "session_id": None,
                "cost_usd": None,
            }

        return {
            "success": True,
            "result": data.get("result", data),
            "session_id": data.get("session_id", None),
            "cost_usd": data.get("total_cost_usd", None),
        }

    except subprocess.TimeoutExpired:
        return {
            "success": False,
            "result": "claude timed out after 600s",
            "session_id": None,
            "cost_usd": None,
        }
    except FileNotFoundError:
        return {
            "success": False,
            "result": "claude CLI not found in PATH",
            "session_id": None,
            "cost_usd": None,
        }


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: run_claude.py '<prompt>' [workdir] [max_turns]"}))
        sys.exit(1)

    prompt = sys.argv[1]
    workdir = sys.argv[2] if len(sys.argv) > 2 else None
    max_turns = int(sys.argv[3]) if len(sys.argv) > 3 else 10

    result = run_claude(prompt, workdir=workdir, max_turns=max_turns)
    print(json.dumps(result, indent=2, default=str))
