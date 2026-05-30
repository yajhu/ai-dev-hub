# CLAUDE.md

AI-driven development hub that automates the code generation pipeline — from task to PR, fully automated with Claude Code + SonarQube + GitHub MCP.

## Architecture

SpringBoot 2.7.18 (Java 8) + MyBatis-Plus 3.5.5 + PostgreSQL + Python orchestrator + Claude Code

```
Task (API) → PipelineService → orchestrator/pipeline.py
                                   ├─ PLANNING  → claude (plan)
                                   ├─ CODING    → claude (impl)
                                   ├─ REVIEWING → sonar_check.py (quality gate)
                                   └─ PR_OPEN   → github_pr.py (create PR)
```

## Key Paths

- `src/main/java/com/aidevhub/` — SpringBoot application (controllers, services, mappers, models, config)
- `orchestrator/` — Python pipeline orchestrator (pipeline.py, run_claude.py, sonar_check.py, github_pr.py, db_client.py)
- `src/main/resources/db/schema.sql` — Database schema
- `src/main/resources/templates/` — Thymeleaf UI templates
- `target/ai-dev-hub-1.0.0-SNAPSHOT.jar` — Built application JAR

## Maven Commands

```bash
mvn compile          # Compile only
mvn spring-boot:run  # Run application
mvn package -DskipTests  # Build JAR
```

## Key APIs

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/tasks` | Create task (use `@RestController` → `/tasks`) |
| GET | `/api/tasks` | List tasks |
| GET | `/api/tasks/{id}` | Get task detail |
| PUT | `/api/tasks/{id}/status` | Update task status |
| DELETE | `/api/tasks/{id}` | Delete task |
| POST | `/api/tasks/{id}/execute` | Execute pipeline for task |
| GET | `/api/executions` | List executions (filter by taskId) |
| POST | `/api/projects` | Create project |
| GET | `/api/projects` | List projects |
| GET | `/api/projects/{id}` | Get project |

Web UI routes (Thymeleaf): `/`, `/tasks`, `/tasks/create`, `/tasks/{id}`

Note: Controllers use `@RequestMapping("/tasks")` etc., so REST paths are `/tasks`, `/tasks/{id}`, `/tasks/{id}/execute` — no `/api` prefix in this implementation.

## Pipeline Stages

1. **PLANNING** — Claude generates implementation plan
2. **CODING** — Claude generates code changes
3. **REVIEWING** — SonarQube quality check (bugs, vulnerabilities, code smells, coverage, duplication)
4. **PR_OPEN** — GitHub pull request created with quality metrics in body

## MCP Servers Available

- **SonarQube** — Quality gate checks during REVIEWING stage
- **PostgreSQL** — Task/execution/project persistence
- **GitHub** — PR creation, repo management

## Docker

```bash
docker-compose up  # starts PostgreSQL + app on port 8080
```
