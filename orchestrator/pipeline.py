#!/usr/bin/env python3
"""
Orchestrator pipeline — the core AI workflow engine.

Coordinates Claude Code + SonarQube + GitHub.

Modes:
  --task-id N    Query task JOIN project from PostgreSQL, run full pipeline
  --prompt ...   Direct mode (fallback): skip DB, use CLI params
"""

import argparse
import json
import os
import sys
import traceback

import psycopg2
import psycopg2.extras

from db_client import create_execution, update_execution, update_task_status
from github_pr import create_pr
from run_claude import run_claude
from sonar_check import check_quality


def _db_connect():
    return psycopg2.connect(
        host=os.environ["DB_HOST"],
        port=os.environ["DB_PORT"],
        dbname=os.environ["DB_NAME"],
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
    )


def fetch_task_with_project(task_id):
    """Query task joined with project. Returns merged dict."""
    conn = _db_connect()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("""
                SELECT t.*, p.name AS project_name, p.repo_url, p.sonar_key, p.tech_stack
                FROM task t
                JOIN project p ON t.project_id = p.id
                WHERE t.id = %s
            """, (task_id,))
            row = cur.fetchone()
            if row is None:
                print(f"[pipeline] ERROR: Task {task_id} not found in database")
                sys.exit(1)
            return dict(row)
    finally:
        conn.close()


def run_pipeline(task_id=None, prompt=None, repo=None, workdir=None):
    """Execute full development pipeline.

    --task-id mode: fetches task+project from DB, extracts:
        prompt = task.title
        workdir = '/tmp/build'
        repo = project.repo_url

    Fallback mode: uses prompt/repo/workdir from CLI args directly.
    """
    result = {"task_id": task_id, "success": False, "stages": {}}

    # ── Resolve params ──────────────────────────────────────────────────
    if task_id is not None:
        print(f"[pipeline] Task ID: {task_id}")
        print(f"[pipeline] Connecting to PostgreSQL...")
        task = fetch_task_with_project(task_id)
        prompt = task.get("title", "Untitled")
        repo = task.get("repo_url") or os.environ.get("GITHUB_REPO", "")
        workdir = "/tmp/build"
        result["task"] = task
        branch_prefix = f"ai-dev/{task_id}"
        print(f"[pipeline] Prompt: {prompt[:100]}")
        print(f"[pipeline] Repo: {repo}")
        print(f"[pipeline] Workdir: {workdir}")
    else:
        print(f"[pipeline] Direct mode (no task ID)")
        print(f"[pipeline] Prompt: {prompt[:100] if prompt else 'N/A'}")
        print(f"[pipeline] Repo: {repo}")
        print(f"[pipeline] Workdir: {workdir}")
        branch_prefix = f"ai-dev/direct-{os.getpid()}"
        task = {"title": prompt, "description": "", "repo_url": repo}

    if not prompt:
        print("[pipeline] ERROR: No prompt provided")
        sys.exit(1)
    if not repo:
        print("[pipeline] WARNING: No repo configured — PR stage will be skipped")

    # ── Stage: PLANNING ─────────────────────────────────────────────────
    print("[pipeline] === PLANNING ===")
    try:
        if task_id:
            update_task_status(task_id, "PLANNING")
            exec_plan = create_execution(task_id, "PLANNING", "RUNNING")

        plan_prompt = (
            f"You are an AI software engineer. Generate a detailed implementation plan "
            f"for the following task:\n\n"
            f"Title: {prompt}\n"
            f"Description: {task.get('description', 'No description')}\n"
            f"Repository: {repo}\n\n"
            f"Output a numbered list of steps with file paths and changes needed."
        )

        plan_result = run_claude(plan_prompt, max_turns=5)

        if plan_result["success"]:
            if task_id:
                update_execution(exec_plan["id"], "COMPLETED", plan_result["result"])
            result["stages"]["planning"] = {"status": "completed"}
            print("[pipeline] PLANNING completed")
        else:
            if task_id:
                update_execution(exec_plan["id"], "FAILED", plan_result["result"])
            result["stages"]["planning"] = {"status": "failed", "error": plan_result["result"]}
            print(f"[pipeline] PLANNING failed: {str(plan_result['result'])[:200]}")
            return result
    except Exception:
        result["stages"]["planning"] = {"status": "error", "error": traceback.format_exc()}
        print(f"[pipeline] PLANNING error: {traceback.format_exc()[:200]}")
        return result

    # ── Stage: CODING ───────────────────────────────────────────────────
    print("[pipeline] === CODING ===")
    try:
        if task_id:
            update_task_status(task_id, "CODING")
            exec_code = create_execution(task_id, "CODING", "RUNNING")

        code_prompt = (
            f"You are an AI software engineer. Implement the following task "
            f"based on the plan below.\n\n"
            f"Task: {prompt}\n"
            f"Description: {task.get('description', '')}\n"
            f"Repository: {repo}\n\n"
            f"Implementation Plan:\n{json.dumps(plan_result['result'], indent=2)}\n\n"
            f"Write all necessary code changes. Create or modify files as needed. "
            f"Work in the provided working directory."
        )

        code_result = run_claude(code_prompt, max_turns=20)

        if code_result["success"]:
            if task_id:
                update_execution(exec_code["id"], "COMPLETED", code_result["result"])
            result["stages"]["coding"] = {"status": "completed"}
            print("[pipeline] CODING completed")
        else:
            if task_id:
                update_execution(exec_code["id"], "FAILED", code_result["result"])
            result["stages"]["coding"] = {"status": "failed", "error": code_result["result"]}
            print(f"[pipeline] CODING failed: {str(code_result['result'])[:200]}")
            return result
    except Exception:
        result["stages"]["coding"] = {"status": "error", "error": traceback.format_exc()}
        print(f"[pipeline] CODING error: {traceback.format_exc()[:200]}")
        return result

    # ── Stage: REVIEWING ────────────────────────────────────────────────
    print("[pipeline] === REVIEWING ===")
    try:
        if task_id:
            update_task_status(task_id, "REVIEWING")
            exec_review = create_execution(task_id, "REVIEWING", "RUNNING")

        project_key = os.environ.get("SONARQUBE_PROJECT_KEY", repo.replace("/", "_"))
        quality_report = check_quality(project_key)

        if "error" in quality_report:
            if task_id:
                update_execution(exec_review["id"], "FAILED", quality_report)
            result["stages"]["reviewing"] = {"status": "failed", "error": quality_report["error"]}
            print(f"[pipeline] REVIEWING failed: {quality_report['error']}")
            return result

        if task_id:
            update_execution(exec_review["id"], "COMPLETED", quality_report)
        result["stages"]["reviewing"] = {
            "status": "completed",
            "quality_passed": quality_report["passed"],
            "metrics": quality_report["metrics"],
        }
        print(f"[pipeline] REVIEWING completed (passed={quality_report['passed']})")
    except Exception:
        result["stages"]["reviewing"] = {"status": "error", "error": traceback.format_exc()}
        print(f"[pipeline] REVIEWING error: {traceback.format_exc()[:200]}")
        return result

    # ── Stage: PR_OPEN ──────────────────────────────────────────────────
    print("[pipeline] === PR_OPEN ===")
    try:
        if task_id:
            update_task_status(task_id, "PR_OPEN")
            exec_pr = create_execution(task_id, "PR_OPEN", "RUNNING")

        if not repo:
            if task_id:
                update_execution(exec_pr["id"], "FAILED", "No repo configured")
            result["stages"]["pr_open"] = {"status": "failed", "error": "No repo configured"}
            print("[pipeline] PR_OPEN skipped: no repo configured")
            return result

        pr_title = prompt[:80] if task_id is None else task.get("title", f"AI Dev: Task {task_id}")
        pr_body = (
            f"## AI-Generated Implementation\n\n"
            f"**Task ID:** {task_id or 'N/A'}\n"
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
            head_branch=branch_prefix,
            base_branch=task.get("base_branch", task.get("target_branch", "main")),
            body=pr_body,
        )

        if "error" in pr_result:
            if task_id:
                update_execution(exec_pr["id"], "FAILED", pr_result)
            result["stages"]["pr_open"] = {"status": "failed", "error": pr_result["error"]}
            print(f"[pipeline] PR_OPEN failed: {pr_result['error']}")
            return result

        if task_id:
            update_execution(exec_pr["id"], "COMPLETED", pr_result)
        result["stages"]["pr_open"] = {
            "status": "completed",
            "pr_url": pr_result["pr_url"],
            "pr_number": pr_result["pr_number"],
        }
        print(f"[pipeline] PR_OPEN completed: {pr_result.get('pr_url')}")
    except Exception:
        result["stages"]["pr_open"] = {"status": "error", "error": traceback.format_exc()}
        print(f"[pipeline] PR_OPEN error: {traceback.format_exc()[:200]}")
        return result

    # ── Done ─────────────────────────────────────────────────────────────
    if task_id:
        update_task_status(task_id, "COMPLETED")
    result["success"] = True
    result["pr_url"] = pr_result.get("pr_url")
    print(f"[pipeline] Pipeline completed successfully")
    return result


def main():
    parser = argparse.ArgumentParser(
        description="AI Dev Hub Orchestrator — run the full pipeline."
    )
    parser.add_argument(
        "--task-id", type=int, default=None,
        help="Task ID to process (fetches task+project from PostgreSQL)"
    )
    parser.add_argument("--prompt", type=str, default=None, help="Direct prompt (fallback mode)")
    parser.add_argument("--repo", type=str, default=None, help="GitHub repo URL (fallback mode)")
    parser.add_argument("--workdir", type=str, default="/tmp/build", help="Working directory")
    args = parser.parse_args()

    if args.task_id is not None:
        result = run_pipeline(task_id=args.task_id)
    elif args.prompt:
        result = run_pipeline(
            prompt=args.prompt,
            repo=args.repo or os.environ.get("GITHUB_REPO", ""),
            workdir=args.workdir,
        )
    else:
        print("[pipeline] ERROR: Either --task-id or --prompt is required")
        sys.exit(1)

    print(json.dumps(result, indent=2, default=str))
    sys.exit(0 if result["success"] else 1)


if __name__ == "__main__":
    main()
