#!/usr/bin/env python3
"""SonarQube quality gate check."""

import json
import os
import sys

import requests


def check_quality(project_key):
    """
    Fetch quality metrics from SonarQube for a given project.

    Args:
        project_key: SonarQube project key.

    Returns:
        dict with metrics, pass/fail status, and raw values.
    """
    sonar_url = os.environ.get("SONARQUBE_URL", "").rstrip("/")
    sonar_token = os.environ.get("SONARQUBE_TOKEN", "")

    if not sonar_url:
        return {"error": "SONARQUBE_URL environment variable not set"}
    if not sonar_token:
        return {"error": "SONARQUBE_TOKEN environment variable not set"}

    metrics = "bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density"

    try:
        resp = requests.get(
            f"{sonar_url}/api/measures/component",
            params={"component": project_key, "metricKeys": metrics},
            auth=(sonar_token, ""),
            timeout=30,
        )
        resp.raise_for_status()
    except requests.RequestException as e:
        return {"error": f"SonarQube API request failed: {e}"}

    data = resp.json()

    if "component" not in data:
        return {"error": "SonarQube response missing component", "raw": data}

    measures = data["component"].get("measures", [])
    metrics_map = {}
    for m in measures:
        metrics_map[m["metric"]] = {
            "value": m.get("value"),
            "bestValue": m.get("bestValue", False),
        }

    # Quality gate: pass if bugs=0, vulnerabilities=0, coverage >= 80
    bugs = int(metrics_map.get("bugs", {}).get("value", 0))
    vulnerabilities = int(metrics_map.get("vulnerabilities", {}).get("value", 0))
    code_smells = int(metrics_map.get("code_smells", {}).get("value", 0))
    coverage = float(metrics_map.get("coverage", {}).get("value", 0))
    duplication = float(metrics_map.get("duplicated_lines_density", {}).get("value", 0))

    passed = bugs == 0 and vulnerabilities == 0 and coverage >= 80.0

    return {
        "project_key": project_key,
        "metrics": {
            "bugs": bugs,
            "vulnerabilities": vulnerabilities,
            "code_smells": code_smells,
            "coverage": coverage,
            "duplicated_lines_density": duplication,
        },
        "passed": passed,
        "checks": {
            "bugs": bugs == 0,
            "vulnerabilities": vulnerabilities == 0,
            "coverage": coverage >= 80.0,
        },
    }


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: sonar_check.py <project_key>"}))
        sys.exit(1)

    result = check_quality(sys.argv[1])
    print(json.dumps(result, indent=2, default=str))
