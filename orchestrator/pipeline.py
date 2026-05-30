#!/usr/bin/env python3
"""
Orchestrator pipeline — the core AI workflow engine.

Coordinates Claude Code + SonarQube + GitHub through a PostgreSQL-backed
task queue. Each pipeline step is tracked via executions in the DB.
"""

import argparse
import json
import os
import sys
import traceback

from db_client import create_execution, get_task, update_execution, update_task_status
from github_pr import create_pr
from run_claude import run_claude
from sonar_check import check_quality


def run_pipeline(task_id):
    """
    Execute the full development pipeline for a given task.

    Steps:
      1. Fetch task from DB
      2. PLANNING  → Claude Code generates implementation plan
      3. CODING    → Claude Code generates code changes
      4. REVIEWING → SonarQube quality check
      5. PR_OPEN   → GitHub pull request creation

    Returns:
        dict with success status and result payload.
    """
    result = {"task_id": task_id, "success": False, "stages": {}}

    # ── Step 1: Fetch task ───────────────────────────────────────────────
    task = get_task(task_id)
    if "error" in task:
        result["error"] = task["error"]
        return result

    result["task"] = task
    repo = task.get("repo", os.environ.get("GITHUB_REPO", ""))
    branch_prefix = f"ai-dev/{task_id}"

    # ── Step 2: PLANNING ─────────────────────────────────────────────────
    try:
        update_task_status(task_id, "PLANNING")
        exec_plan = create_execution(task_id, "PLANNING", "RUNNING")

        plan_prompt = (
            f"You are an AI software engineer. Generate a detailed implementation plan "
            f"for the following task:\n\n"
            f"Title: {task.get('title', 'Untitled')}\n"
            f"Description: {task.get('description', 'No description')}\n"
            f"Repository: {repo}\n\n"
            f"Output a numbered list of steps with file paths and changes needed."
        )

        plan_result = run_claude(plan_prompt, max_turns=5)

        if plan_result["success"]:
            update_execution(exec_plan["id"], "COMPLETED", plan_result["result"])
            result["stages"]["planning"] = {
                "status": "completed",
                "execution_id": exec_plan["id"],
            }
        else:
            update_execution(exec_plan["id"], "FAILED", plan_result["result"])
            result["stages"]["planning"] = {
                "status": "failed",
                "error": plan_result["result"],
            }
            return result
    except Exception:
        result["stages"]["planning"] = {
            "status": "error",
            "error": traceback.format_exc(),
        }
        return result

    # ── Step 3: CODING ───────────────────────────────────────────────────
    try:
        update_task_status(task_id, "CODING")
        exec_code = create_execution(task_id, "CODING", "RUNNING")

        code_prompt = (
            f"You are an AI software engineer. Implement the following task "
            f"based on the plan below.\n\n"
            f"Task: {task.get('title', '')}\n"
            f"Description: {task.get('description', '')}\n"
            f"Repository: {repo}\n\n"
            f"Implementation Plan:\n{json.dumps(plan_result['result'], indent=2)}\n\n"
            f"Write all necessary code changes. Create or modify files as needed. "
            f"Work in the provided working directory."
        )

        code_result = run_claude(code_prompt, max_turns=20)

        if code_result["success"]:
            update_execution(exec_code["id"], "COMPLETED", code_result["result"])
            result["stages"]["coding"] = {
                "status": "completed",
                "execution_id": exec_code["id"],
            }
        else:
            update_execution(exec_code["id"], "FAILED", code_result["result"])
            result["stages"]["coding"] = {
                "status": "failed",
                "error": code_result["result"],
            }
            return result
    except Exception:
        result["stages"]["coding"] = {
            "status": "error",
            "error": traceback.format_exc(),
        }
        return result

    # ── Step 4: REVIEWING (SonarQube) ────────────────────────────────────
    try:
        update_task_status(task_id, "REVIEWING")
        exec_review = create_execution(task_id, "REVIEWING", "RUNNING")

        project_key = os.environ.get("SONARQUBE_PROJECT_KEY", repo.replace("/", "_"))
        quality_report = check_quality(project_key)

        if "error" in quality_report:
            update_execution(exec_review["id"], "FAILED", quality_report)
            result["stages"]["reviewing"] = {
                "status": "failed",
                "error": quality_report["error"],
            }
            return result

        update_execution(exec_review["id"], "COMPLETED", quality_report)
        result["stages"]["reviewing"] = {
            "status": "completed",
            "execution_id": exec_review["id"],
            "quality_passed": quality_report["passed"],
            "metrics": quality_report["metrics"],
        }
    except Exception:
        result["stages"]["reviewing"] = {
            "status": "error",
            "error": traceback.format_exc(),
        }
        return result

    # ── Step 5: PR_OPEN ──────────────────────────────────────────────────
    try:
        update_task_status(task_id, "PR_OPEN")
        exec_pr = create_execution(task_id, "PR_OPEN", "RUNNING")

        if not repo:
            update_execution(exec_pr["id"], "FAILED", "No repo configured")
            result["stages"]["pr_open"] = {"status": "failed", "error": "No repo configured"}
            return result

        pr_title = task.get("title", f"AI Dev: Task {task_id}")
        pr_body = (
            f"## AI-Generated Implementation\n\n"
            f"**Task ID:** {task_id}\n"
            f"**Description:** {task.get('description', 'N/A')}\n\n"
            f"### Quality Report\n"
            f"- Bugs: {quality_report['metrics']['bugs']}\n"
            f"- Vulnerabilities: {quality_report['metrics']['vulnerabilities']}\n"
            f"- Code Smells: {quality_report['metrics']['code_smells']}\n"
            f"- Coverage: {quality_report['metrics']['coverage']}%\n"
            f"- Duplication: {quality_report['metrics']['duplicated_lines_density']}%\n"
        )

        pr_result = create_pr(
            repo=repo,
            title=pr_title,
            head_branch=f"{branch_prefix}",
            base_branch=task.get("base_branch", "main"),
            body=pr_body,
        )

        if "error" in pr_result:
            update_execution(exec_pr["id"], "FAILED", pr_result)
            result["stages"]["pr_open"] = {"status": "failed", "error": pr_result["error"]}
            return result

        update_execution(exec_pr["id"], "COMPLETED", pr_result)
        result["stages"]["pr_open"] = {
            "status": "completed",
            "execution_id": exec_pr["id"],
            "pr_url": pr_result["pr_url"],
            "pr_number": pr_result["pr_number"],
        }
    except Exception:
        result["stages"]["pr_open"] = {
            "status": "error",
            "error": traceback.format_exc(),
        }
        return result

    # ── Done ─────────────────────────────────────────────────────────────
    update_task_status(task_id, "COMPLETED")
    result["success"] = True
    result["pr_url"] = pr_result.get("pr_url")
    return result


def main():
    parser = argparse.ArgumentParser(
        description="AI Dev Hub Orchestrator — run the full pipeline for a task."
    )
    parser.add_argument(
        "--task-id", type=int, required=True, help="Task ID to process"
    )
    args = parser.parse_args()

    result = run_pipeline(args.task_id)
    print(json.dumps(result, indent=2, default=str))
    sys.exit(0 if result["success"] else 1)


if __name__ == "__main__":
    main()
