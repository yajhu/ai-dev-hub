#!/usr/bin/env python3
"""GitHub PR operations via REST API."""

import json
import os
import sys

import requests


def _github_api_headers():
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token:
        raise ValueError("GITHUB_TOKEN environment variable not set")
    return {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }


def create_pr(repo, title, head_branch, base_branch, body=""):
    """
    Create a pull request on GitHub.

    Args:
        repo: Repository name as 'owner/repo'.
        title: PR title.
        head_branch: Source branch name.
        base_branch: Target branch name (e.g. 'main').
        body: PR description body.

    Returns:
        dict with pr_url, pr_number, or error.
    """
    if "/" not in repo:
        return {"error": "repo must be in 'owner/repo' format"}

    headers = _github_api_headers()
    url = f"https://api.github.com/repos/{repo}/pulls"

    payload = {
        "title": title,
        "head": head_branch,
        "base": base_branch,
        "body": body,
    }

    try:
        resp = requests.post(url, headers=headers, json=payload, timeout=30)
        resp.raise_for_status()
    except requests.HTTPError as e:
        return {"error": f"GitHub API error: {e}", "detail": resp.text}
    except requests.RequestException as e:
        return {"error": f"GitHub API request failed: {e}"}

    data = resp.json()
    return {
        "pr_url": data.get("html_url"),
        "pr_number": data.get("number"),
    }


def create_branch(repo, branch_name, base_sha):
    """
    Create a new branch reference in a GitHub repo.

    Args:
        repo: Repository name as 'owner/repo'.
        branch_name: New branch name (e.g. 'feature/foo').
        base_sha: SHA to branch from.

    Returns:
        dict with ref or error.
    """
    if "/" not in repo:
        return {"error": "repo must be in 'owner/repo' format"}

    headers = _github_api_headers()
    url = f"https://api.github.com/repos/{repo}/git/refs"

    payload = {
        "ref": f"refs/heads/{branch_name}",
        "sha": base_sha,
    }

    try:
        resp = requests.post(url, headers=headers, json=payload, timeout=30)
        resp.raise_for_status()
    except requests.HTTPError as e:
        return {"error": f"GitHub API error: {e}", "detail": resp.text}
    except requests.RequestException as e:
        return {"error": f"GitHub API request failed: {e}"}

    data = resp.json()
    return {
        "ref": data.get("ref"),
        "sha": data.get("object", {}).get("sha"),
    }


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({
            "error": "Usage: github_pr.py create_pr <repo> <title> <head> <base> [body]"
        }))
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "create_pr":
        repo = sys.argv[2]
        title = sys.argv[3]
        head_branch = sys.argv[4]
        base_branch = sys.argv[5]
        body = sys.argv[6] if len(sys.argv) > 6 else ""
        result = create_pr(repo, title, head_branch, base_branch, body)
    elif cmd == "create_branch":
        repo = sys.argv[2]
        branch_name = sys.argv[3]
        base_sha = sys.argv[4]
        result = create_branch(repo, branch_name, base_sha)
    else:
        result = {"error": f"Unknown command: {cmd}"}

    print(json.dumps(result, indent=2, default=str))
