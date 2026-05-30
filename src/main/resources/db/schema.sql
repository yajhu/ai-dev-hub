CREATE TABLE IF NOT EXISTS project (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    repo_url    VARCHAR(500) NOT NULL,
    tech_stack  TEXT,
    sonar_key   VARCHAR(200),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS task (
    id            BIGSERIAL PRIMARY KEY,
    project_id    BIGINT NOT NULL REFERENCES project(id),
    title         VARCHAR(300) NOT NULL,
    description   TEXT,
    acceptance    TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    priority      VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    source        VARCHAR(20),
    ai_model      VARCHAR(50),
    target_branch VARCHAR(100),
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS execution (
    id           BIGSERIAL PRIMARY KEY,
    task_id      BIGINT NOT NULL REFERENCES task(id),
    stage        VARCHAR(30) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    output       TEXT,
    sonar_report JSONB,
    pr_url       VARCHAR(500),
    deploy_url   VARCHAR(500),
    started_at   TIMESTAMP,
    finished_at  TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS quality_gate (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES project(id),
    rule_type   VARCHAR(30)  NOT NULL,
    rule_config JSONB NOT NULL DEFAULT '{}',
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_project_id ON task(project_id);
CREATE INDEX IF NOT EXISTS idx_task_status ON task(status);
CREATE INDEX IF NOT EXISTS idx_execution_task_id ON execution(task_id);
CREATE INDEX IF NOT EXISTS idx_quality_gate_project_id ON quality_gate(project_id);
