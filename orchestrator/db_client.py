#!/usr/bin/env python3
"""PostgreSQL database client for the orchestrator."""

import json
import os
import sys

import psycopg2
import psycopg2.extras


def _get_conn():
    return psycopg2.connect(
        host=os.environ["DB_HOST"],
        port=os.environ["DB_PORT"],
        dbname=os.environ["DB_NAME"],
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
    )


def get_task(task_id):
    """Fetch a task by ID. Returns a dict."""
    conn = _get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute("SELECT * FROM task WHERE id = %s", (task_id,))
            row = cur.fetchone()
            if row is None:
                return {"error": f"Task {task_id} not found"}
            return dict(row)
    finally:
        conn.close()


def update_task_status(task_id, status):
    """Update task status. Returns updated row."""
    conn = _get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(
                "UPDATE task SET status = %s, updated_at = NOW() WHERE id = %s RETURNING *",
                (status, task_id),
            )
            row = cur.fetchone()
            conn.commit()
            if row is None:
                return {"error": f"Task {task_id} not found"}
            return dict(row)
    finally:
        conn.close()


def create_execution(task_id, stage, status):
    """Create an execution record. Returns the new execution."""
    conn = _get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(
                """INSERT INTO execution (task_id, stage, status, started_at)
                   VALUES (%s, %s, %s, NOW()) RETURNING *""",
                (task_id, stage, status),
            )
            row = cur.fetchone()
            conn.commit()
            return dict(row)
    finally:
        conn.close()


def update_execution(execution_id, status, output=None):
    """Update execution status and optionally output. Returns updated row."""
    conn = _get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            if output is not None:
                cur.execute(
                    """UPDATE execution
                       SET status = %s, output = %s, updated_at = NOW()
                       WHERE id = %s RETURNING *""",
                    (status, json.dumps(output), execution_id),
                )
            else:
                cur.execute(
                    """UPDATE execution
                       SET status = %s, updated_at = NOW()
                       WHERE id = %s RETURNING *""",
                    (status, execution_id),
                )
            row = cur.fetchone()
            conn.commit()
            if row is None:
                return {"error": f"Execution {execution_id} not found"}
            return dict(row)
    finally:
        conn.close()


if __name__ == "__main__":
    # Quick smoke test
    cmd = sys.argv[1] if len(sys.argv) > 1 else None
    if cmd == "get_task":
        result = get_task(int(sys.argv[2]))
    elif cmd == "update_task_status":
        result = update_task_status(int(sys.argv[2]), sys.argv[3])
    else:
        result = {"error": "Unknown command"}
    print(json.dumps(result, indent=2, default=str))
