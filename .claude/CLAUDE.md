# CLAUDE.md

AI Dev Hub — AI 驱动的开发编排平台。从任务创建到 PR 提交，全流程自动化：Claude Code 生成代码 → SonarQube 质量检查 → GitHub PR 创建。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | SpringBoot 2.7.18 |
| Java 版本 | Java 8 (1.8) |
| 构建工具 | Maven |
| ORM | MyBatis-Plus 3.5.5 |
| 数据库 | PostgreSQL 15 |
| 模板引擎 | Thymeleaf（暗色主题 CSS） |
| Python 编排 | Python 3, psycopg2, requests, pyyaml |
| 容器化 | Docker Compose（多阶段构建） |
| 基础包路径 | com.aidevhub |

---

## 项目结构

```
ai-dev-hub/
├── pom.xml                          # Maven 配置 (SpringBoot 2.7.18 + MyBatis-Plus + Lombok)
├── Dockerfile                       # 多阶段构建 (maven:3.8-openjdk-8 → openjdk:8-jre-slim)
├── docker-compose.yml               # PostgreSQL 15 + 应用服务
├── docker-compose.override.yml
├── docker-compose.test.yml
├── README.md
│
├── orchestrator/                    # Python 编排引擎
│   ├── requirements.txt             # requests, psycopg2-binary, pyyaml
│   ├── pipeline.py                  # 主入口：PLANNING→CODING→REVIEWING→PR_OPEN
│   ├── db_client.py                 # PostgreSQL 客户端 (create_execution, update_execution, update_task_status)
│   ├── run_claude.py                # Claude Code CLI 包装器
│   ├── sonar_check.py               # SonarQube 质量门检查
│   └── github_pr.py                 # GitHub PR 创建 (REST API)
│
└── src/
    └── main/
        ├── java/com/aidevhub/
        │   ├── AiDevHubApplication.java    # 启动类 (@MapperScan)
        │   ├── config/
        │   │   └── MyBatisPlusConfig.java  # 分页插件（注意：当前配置了 DbType.MYSQL，实际使用 PostgreSQL）
        │   ├── controller/
        │   │   ├── TaskController.java     # /api/tasks — CRUD + 状态更新 + 触发执行
        │   │   ├── ProjectController.java  # /api/projects — CRUD
        │   │   ├── ExecutionController.java # /api/executions — 查询执行记录
        │   │   └── WebController.java      # Thymeleaf 页面路由 (/, /tasks, /tasks/create, /tasks/{id})
        │   ├── service/
        │   │   ├── PipelineService.java    # 流水线服务：调用 python3 orchestrator/pipeline.py
        │   │   └── TaskService.java        # 任务 CRUD + 状态机管理
        │   ├── model/
        │   │   ├── Task.java               # 任务实体 (projectId, title, description, acceptance, status, priority, source, aiModel, targetBranch)
        │   │   ├── Project.java            # 项目实体 (name, repoUrl, techStack, sonarKey)
        │   │   ├── Execution.java          # 执行记录 (taskId, stage, status, output, sonarReport, prUrl, deployUrl, startedAt, finishedAt)
        │   │   └── QualityGate.java        # 质量门 (projectId, ruleType, ruleConfig, enabled)
        │   ├── mapper/
        │   │   ├── TaskMapper.java
        │   │   ├── ProjectMapper.java
        │   │   ├── ExecutionMapper.java
        │   │   └── QualityGateMapper.java
        │   └── common/
        │       ├── BaseEntity.java         # 抽象基类 (id, createdAt, updatedAt)
        │       ├── Result.java             # 统一响应 {code, message, data}
        │       ├── TaskStatus.java         # 任务状态枚举 + 状态转换逻辑
        │       ├── StageType.java          # 流水线阶段枚举
        │       ├── TaskStatusUpdateRequest.java
        │       ├── BusinessException.java
        │       └── GlobalExceptionHandler.java  # @RestControllerAdvice
        └── resources/
            ├── application.yml             # 应用配置（端口8080, PostgreSQL, MyBatis-Plus, SonarQube, pipeline）
            ├── db/schema.sql               # 数据库初始化脚本（project, task, execution, quality_gate + 索引）
            ├── templates/                  # Thymeleaf 模板
            │   ├── index.html
            │   ├── tasks.html
            │   ├── task-detail.html
            │   ├── create-task.html
            │   └── error.html
            └── static/
                └── css/style.css           # 暗色主题 (背景 #0d1117, GitHub 风格)
```

---

## 核心架构与数据流

```
用户创建 Task (API) → POST /api/tasks/{id}/execute
                       │
                       ▼
              PipelineService.executeTask(taskId)
                       │
                       ├── 更新任务状态为 PLANNING
                       ├── 创建 Execution 记录
                       └── Runtime.exec("python3 orchestrator/pipeline.py --task-id N")
                               │
                               ▼
                       orchestrator/pipeline.py
                               │
                    ┌──────────┼──────────┐
                    ▼          ▼          ▼
               PLANNING    CODING    REVIEWING     PR_OPEN
              (claude)   (claude)  (sonar_check) (github_pr)
                    │          │          │          │
                    └──────────┴──────────┴──────────┘
                               │
                               ▼
                   任务状态一路推进到 COMPLETED
```

**PipelineService.java 工作原理：**
1. 通过 `Runtime.getRuntime().exec()` 启动 Python 子进程
2. 5 分钟超时，超时则 destroyForcibly()
3. 读 stdout 作为输出，stderr 作为错误信息
4. Python 退出码非 0 时标记 FAILED
5. 成功后自动推进到 CODING 状态

**重要：PipelineService.java 中脚本路径硬编码为：**
```java
private static final String PIPELINE_SCRIPT = "/home/huyajun/workspace/ai-dev-hub/scripts/pipeline.py";
```
但实际 Python 编排脚本位于 `orchestrator/pipeline.py`。这可能是一个已知的路径不一致问题，需要根据部署环境调整。

---

## 数据库表结构

### project（项目表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 自增主键 |
| name | VARCHAR(200) | 项目名称 |
| repo_url | VARCHAR(500) | GitHub 仓库 URL |
| tech_stack | TEXT | 技术栈描述 |
| sonar_key | VARCHAR(200) | SonarQube 项目 key |
| created_at / updated_at | TIMESTAMP | 时间戳 |

### task（任务表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 自增主键 |
| project_id | BIGINT FK→project | 所属项目 |
| title | VARCHAR(300) | 任务标题 |
| description / acceptance | TEXT | 描述和验收标准 |
| status | VARCHAR(20) | 状态枚举 (PENDING/PLANNING/CODING/REVIEWING/PR_OPEN/DEPLOYED/FAILED) |
| priority | VARCHAR(10) | 优先级 (默认 MEDIUM) |
| source | VARCHAR(20) | 来源标识 |
| ai_model | VARCHAR(50) | 使用的 AI 模型 |
| target_branch | VARCHAR(100) | 目标分支 |
| created_at / updated_at | TIMESTAMP | 时间戳 |

索引：idx_task_project_id, idx_task_status

### execution（执行记录表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 自增主键 |
| task_id | BIGINT FK→task | 关联任务 |
| stage | VARCHAR(30) | 阶段名称 (PLANNING/CODING/REVIEWING/PR_OPEN) |
| status | VARCHAR(20) | 执行状态 (PENDING/RUNNING/COMPLETED/FAILED) |
| output | TEXT | 执行输出 |
| sonar_report | JSONB | SonarQube 报告 |
| pr_url | VARCHAR(500) | PR URL |
| deploy_url | VARCHAR(500) | 部署 URL |
| started_at / finished_at | TIMESTAMP | 开始/结束时间 |
| created_at / updated_at | TIMESTAMP | 时间戳 |

索引：idx_execution_task_id

### quality_gate（质量门表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 自增主键 |
| project_id | BIGINT FK→project | 所属项目 |
| rule_type | VARCHAR(30) | 规则类型 |
| rule_config | JSONB | 规则配置 |
| enabled | BOOLEAN | 是否启用 |
| created_at / updated_at | TIMESTAMP | 时间戳 |

索引：idx_quality_gate_project_id

---

## 状态机

### TaskStatus 枚举 (Java)

```
PENDING ──→ PLANNING ──→ CODING ──→ REVIEWING ──→ PR_OPEN ──→ DEPLOYED
   │           │           │           │             │
   └───────────┴───────────┴───────────┴─────────────┘
                        ↓
                      FAILED
```

- **前向流转**：只能按顺序进行 (PENDING→PLANNING→CODING→...)，不可跳步
- **FAILED**：任意非终态均可转入 FAILED
- **终态**：DEPLOYED 和 FAILED，不可再变更
- **状态验证**：`TaskStatus.canTransitionTo(target)` 和 `TaskService.updateStatus()` 均做校验

### TaskService 关键方法

| 方法 | 说明 |
|------|------|
| `createTask(task)` | 创建任务，初始状态 PENDING |
| `getTask(id)` | 查询单任务，不存在抛 IllegalArgumentException |
| `listTasks(projectId, page, size)` | 按项目分页查询 |
| `updateTask(id, input)` | 更新 title/description/acceptance/priority（终态不可改） |
| `deleteTask(id)` | 删除任务 |
| `updateStatus(taskId, newStatus)` | 状态变更（含前向校验） |
| `markFailed(taskId)` | 将任意非终态任务标记为 FAILED |

---

## REST API 接口

所有接口的基础路径均带 `/api` 前缀（控制器 `@RequestMapping` 设置）。

### 任务接口 (/api/tasks)

| Method | Path | 说明 |
|--------|------|------|
| POST | /api/tasks | 创建任务 (Body: Task JSON) |
| GET | /api/tasks | 分页查询 (Query: projectId, page, size) |
| GET | /api/tasks/{id} | 查询单个任务 |
| PUT | /api/tasks/{id} | 更新任务 (Body: Task JSON) |
| DELETE | /api/tasks/{id} | 删除任务 |
| PUT | /api/tasks/{id}/status | 更新任务状态 (Body: {"status": "PLANNING"}) |
| POST | /api/tasks/{id}/execute | 触发流水线执行 → PipelineService.executeTask() |

### 项目接口 (/api/projects)

| Method | Path | 说明 |
|--------|------|------|
| POST | /api/projects | 创建项目 |
| GET | /api/projects | 分页查询 (Query: current, size) |
| GET | /api/projects/{id} | 查询单个项目 |
| PUT | /api/projects/{id} | 更新项目 |
| DELETE | /api/projects/{id} | 删除项目 |

### 执行记录接口 (/api/executions)

| Method | Path | 说明 |
|--------|------|------|
| GET | /api/executions | 分页查询 (Query: taskId 必填, current, size) |
| GET | /api/executions/{id} | 查询单个执行记录 |

### Thymeleaf 页面路由

| Path | 模板 | 说明 |
|------|------|------|
| / | index.html | 首页，展示项目列表 |
| /tasks?projectId=N | tasks.html | 任务列表 |
| /tasks/create?projectId=N | create-task.html | 创建任务表单 |
| /tasks/{id} | task-detail.html | 任务详情，含执行记录和 PR 链接 |

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": ...
}
```

错误时 code 为 400/500，message 包含错误描述。全局异常处理覆盖 IllegalStateException、IllegalArgumentException、Exception。

---

## Python 编排器详解

### orchestrator/pipeline.py — 主流水线

入口模式：
- `--task-id N`：从 PostgreSQL 查询 task JOIN project，自动运行完整流水线
- `--prompt "..." --repo "owner/repo"`：直接模式，跳过数据库

**四阶段执行：**

1. **PLANNING** — 调用 `run_claude()` 生成实现计划（max_turns=5）
2. **CODING** — 调用 `run_claude()` 根据计划生成代码（max_turns=20）
3. **REVIEWING** — 调用 `check_quality()` 从 SonarQube 拉取质量指标
4. **PR_OPEN** — 调用 `create_pr()` 在 GitHub 创建 Pull Request

每个阶段通过 `db_client.py` 写入 execution 表和 task 状态。

### orchestrator/db_client.py — 数据库客户端

通过环境变量连接 PostgreSQL：
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`

关键函数：
- `get_task(task_id)` — 查询任务
- `update_task_status(task_id, status)` — 更新任务状态
- `create_execution(task_id, stage, status)` — 创建执行记录
- `update_execution(execution_id, status, output)` — 更新执行记录

注意：pipeline.py 最终调用 `update_task_status(task_id, "COMPLETED")`，但 Java 端 TaskStatus 枚举中并无 COMPLETED，只有 DEPLOYED。这是 Java 端和 Python 端状态名不一致的地方。

### orchestrator/run_claude.py — Claude Code CLI 包装

- 使用 `subprocess.run()` 调用 `claude` 命令行
- 参数：`--dangerously-skip-permissions -p "prompt" --max-turns N --output-format json`
- 10 分钟超时（timeout=600）
- 返回 JSON 格式结果 {success, result, session_id, cost_usd}
- 需要 `claude` 在 PATH 中

### orchestrator/sonar_check.py — SonarQube 质量门

- 环境变量：`SONARQUBE_URL`, `SONARQUBE_TOKEN`
- 拉取指标：bugs, vulnerabilities, code_smells, coverage, duplicated_lines_density
- 通过标准：bugs=0 AND vulnerabilities=0 AND coverage >= 80%
- 返回 {project_key, metrics, passed, checks}

### orchestrator/github_pr.py — GitHub PR 创建

- 环境变量：`GITHUB_TOKEN`
- 通过 GitHub REST API 创建 PR
- create_pr(repo, title, head_branch, base_branch, body)
- create_branch(repo, branch_name, base_sha)
- repo 格式必须为 "owner/repo"

### orchestrator/requirements.txt

```
requests>=2.31.0
psycopg2-binary>=2.9.9
pyyaml>=6.0
```

---

## 构建与运行

### 本地开发

```bash
# 编译
mvn clean compile

# 打包（跳过测试）
mvn clean package -DskipTests

# 运行 JAR
java -jar target/ai-dev-hub-1.0.0-SNAPSHOT.jar

# 或直接 Spring Boot 运行
mvn spring-boot:run
```

应用启动后监听 `http://localhost:8080`，无 context-path。

### Docker 部署

```bash
# 启动全部服务 (PostgreSQL 15 + 应用)
docker compose up -d --build

# 停止
docker compose down
```

**docker-compose.yml 关键配置：**
- PostgreSQL 15 容器：DB aidevhub, 用户/密码 aidevhub/aidevhub
- schema.sql 自动初始化（挂载到 `/docker-entrypoint-initdb.d/`）
- 健康检查：`pg_isready -U aidevhub -d aidevhub`
- 应用容器环境变量包括 `DB_HOST=postgres`, `DB_PORT=5432`, `DB_NAME=aidevhub`, `DB_USER=aidevhub`, `DB_PASSWORD=aidevhub`

**Dockerfile 多阶段构建：**
1. 阶段 1 (builder)：`maven:3.8-openjdk-8` — 先下载依赖，再编译打包
2. 阶段 2 (runtime)：`openjdk:8-jre-slim` — 安装 Python3 + pip，复制 JAR 和 orchestrator/，安装 Python 依赖

---

## 配置说明 (application.yml)

```yaml
server.port: 8080                                    # 应用端口
spring.datasource.url: jdbc:postgresql://...         # 本地开发指向 localhost:5432/aidevhub
spring.datasource.username: root                     # 本地开发用户
spring.datasource.password: aidevhub123              # 本地开发密码
spring.thymeleaf.cache: false                        # 开发模式关闭缓存
mybatis-plus.configuration.map-underscore-to-camel-case: true  # 下划线转驼峰
mybatis-plus.configuration.log-impl: StdOutImpl      # SQL 日志输出
mybatis-plus.global-config.db-config.id-type: auto   # 主键自增
sonarqube.url: http://localhost:9000                 # SonarQube 地址
pipeline.workdir: /home/huyajun/workspace/ai-dev-hub # 流水线工作目录
```

**注意：** 本地开发和 Docker 部署使用不同的数据库连接参数。Docker 中通过 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` 环境变量覆盖。

---

## 重要约定与注意事项

1. **BaseEntity**：所有模型类继承 `BaseEntity`（提供 id, createdAt, updatedAt），Lombok `@Data` 生成 getter/setter
2. **Mapper 接口**：全部继承 `MyBatis-Plus BaseMapper<T>`，无需写 SQL，CRUD 自动生成
3. **表名映射**：使用 `@TableName` 注解显式指定（如 `@TableName("task")`）
4. **下划线转驼峰**：`map-underscore-to-camel-case: true` 已开启，Java 字段 `projectId` 自动映射数据库列 `project_id`
5. **Lombok 注解处理**：pom.xml 中 maven-compiler-plugin 已配置 annotationProcessorPaths，IDE 需安装 Lombok 插件
6. **分页插件**：MyBatisPlusConfig 中配置了 `PaginationInnerInterceptor`，但当前设置为 `DbType.MYSQL`，实际使用 PostgreSQL——分页功能仍可正常工作，但建议修正为 `DbType.POSTGRE_SQL`
7. **PipelineService 脚本路径**：硬编码在 `/home/huyajun/workspace/ai-dev-hub/scripts/pipeline.py`，与实际的 `orchestrator/pipeline.py` 不一致，Docker 中 Python 脚本在 `/app/orchestrator/` 目录下
8. **Python/Java 状态不一致**：pipeline.py 在成功后调用 `update_task_status(task_id, "COMPLETED")`（第 289 行），但 TaskStatus 枚举没有 COMPLETED 值，只有 DEPLOYED——这是从旧实现遗留的差异
9. **WebController 使用字段注入**：不同于其他控制器使用 `@RequiredArgsConstructor` 构造器注入，WebController 使用 `@Autowired` 字段注入
10. **Task 模型关联 Project**：Task.java 中 `@TableField(exist = false)` 标注 project 字段，表示不映射到数据库，由业务逻辑手动填充

---

## 暗色主题

static/css/style.css 提供 GitHub 风格暗色主题：
- 主背景：`#0d1117`
- 次级背景：`#161b22`, `#21262d`
- 边框：`#30363d`
- 主文字：`#e6edf3`
- 强调色：`#58a6ff`
- 成功/危险：`#3fb950` / `#f85149`

---

## 快速调试指南

```bash
# 1. 启动数据库
docker compose up -d postgres

# 2. 启动应用（不依赖 Docker）
mvn spring-boot:run

# 3. 创建项目
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -d '{"name":"测试项目","repoUrl":"https://github.com/user/repo","techStack":"SpringBoot"}'

# 4. 创建任务
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"projectId":1,"title":"添加用户登录功能","description":"实现JWT认证"}'

# 5. 查看任务
curl http://localhost:8080/api/tasks/1

# 6. 触发流水线执行（需要本地有 claude 命令和正确的环境变量）
curl -X POST http://localhost:8080/api/tasks/1/execute

# 7. 查看执行记录
curl "http://localhost:8080/api/executions?taskId=1"
```

---

## 环境变量参考

Python 编排器需要以下环境变量：

| 变量 | 说明 | 必需 |
|------|------|------|
| DB_HOST | PostgreSQL 主机 | 是 |
| DB_PORT | PostgreSQL 端口 | 是 |
| DB_NAME | PostgreSQL 数据库名 | 是 |
| DB_USER | PostgreSQL 用户 | 是 |
| DB_PASSWORD | PostgreSQL 密码 | 是 |
| GITHUB_TOKEN | GitHub Personal Access Token | 创建 PR 时需要 |
| SONARQUBE_URL | SonarQube 服务器 URL | 质量检查时需要 |
| SONARQUBE_TOKEN | SonarQube 认证 Token | 质量检查时需要 |
