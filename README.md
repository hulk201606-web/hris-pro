# 智云 HR（hris-pro）

[![License](https://img.shields.io/github/license/hulk201606-web/hris-pro)](LICENSE)

面向企业的**多租户 SaaS 人力资源管理系统**，覆盖人事、考勤、薪酬、招聘、绩效审批与知识库问答全链路。

## 核心能力

- **多租户隔离**：租户上下文贯穿请求链路（`TraceContext` → Feign 透传 → 下游还原），防跨租户越权
- **流程审批中台**：集成 Flowable 8 工作流引擎，支持请假/加班/补卡/Offer 等业务单的发起与审批
- **薪酬密域**：工资单等敏感金额脱敏返回，密文经专用端点按独立权限点读取（可区分「无功能权限」与「无密域权限」）
- **知识库 + RAG**：文档解析 → 切片 → 向量索引（Milvus）→ 语义检索，当前完成向量库与 Embedding 接入
- **容器化交付**：单一参数化 Dockerfile 构建全部服务，两段 compose（中间件 + 应用）

## 技术栈

| 领域 | 选型 |
|------|------|
| 后端框架 | Spring Boot 4.0.6 + Spring Cloud 2025.1 |
| 流程引擎 | Flowable 8.0.0（原生支持 Boot 4 + Jackson 3） |
| AI / 向量检索 | Spring AI 2.0（OpenAI 兼容协议接入通义千问）、Milvus 2.5 |
| 服务治理 | Nacos 3.1.1（注册配置）、RocketMQ 5.3.1（事件总线） |
| 持久层 | MySQL 8 / PostgreSQL、Redis 7 |
| 前端 | Vue 3 + TypeScript + Vite + Ant Design Vue 4 |
| 构建部署 | Maven 3.9（JDK 21）、Docker Compose |

## 快速开始

### 先决条件

- JDK 21、Maven 3.9
- Node.js 22
- Docker & Docker Compose v2.20+

### 1. 启动中间件

```bash
docker compose up -d                 # Nacos + RocketMQ + Redis
docker compose --profile rag up -d   # 可选：ES + Milvus（RAG 检索）
```

### 2. 初始化数据库与权限

```bash
python -m pip install pymysql

export DB_PASSWORD='<你的数据库密码>'
python scripts/init_db.py                    # 建库建表（仅首次；库已存在时跳过）
python scripts/seed_rbac_from_source.py      # 扫描 @RequirePerm 同步权限点（幂等）
```

### 3. 启动服务

开发调试（逐个前台启动，便于看日志）：

```bash
export JWT_SECRET="$(openssl rand -base64 48)"   # ≥32 字节，各服务必须一致
export DB_PASSWORD='<你的数据库密码>'
bash scripts/start-all.sh                      # 一次拉起全部服务
```

单服务调试：

```bash
bash scripts/run-one.sh hris-auth 8101 auth_db
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

默认账号：`demo` / `admin` / `Admin@123`

### 容器化部署

```bash
cp .env.example .env      # 填写 DB_PASSWORD / JWT_SECRET（必填）
bash docker/build-all.sh # 构建全部服务镜像
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d
```

详见 [部署指南](docs/17-部署指南.md)。

## 测试

```bash
bash scripts/test-all.sh                    # 单元 + 装配测试（133 个用例，无需运行服务）
bash scripts/test-all.sh --e2e              # 追加端到端（需服务就绪，且需 DB_PASSWORD）
bash scripts/test-all.sh --module hris-auth # 只跑指定模块
```

CI 流水线（`.github/workflows/ci.yml`）包含 5 个 job：P0 门禁 / 后端测试 / 前端构建 / 镜像构建 / 汇总。详见 [测试体系](docs/16-测试体系.md)。

## 项目结构

```
hris-pro/
├── hris-gateway/      # 网关（唯一对外入口，路由与 JWT 验签）
├── hris-auth/         # 认证授权（登录、令牌签发/轮换/撤销）
├── hris-org/          # 组织与主数据（法人/部门/岗位/编制）
├── hris-employee/     # 员工档案与生命周期
├── hris-attendance/   # 考勤（排班/日结/月结/请假加班补卡）
├── hris-recruit/      # 招聘（需求/候选人/面试/Offer/入职建档）
├── hris-compensation/ # 薪酬（档案/算薪/工资单/密域）
├── hris-performance/  # 绩效（周期/目标/评估）
├── hris-flow/         # 流程引擎中台
├── hris-rag/          # 知识库与语义检索
├── hris-platform/     # 平台服务（文件/字典/通知）
├── hris-common/       # 公共库（信封/异常/租户透传/权限切面）
├── frontend/          # 单 SPA 双端（自助端 / 管理端）
├── scripts/           # 启动、造数、测试、权限同步脚本
├── docker/            # 参数化 Dockerfile 与构建脚本
└── docs/              # 需求与设计文档集（见 docs/00-README.md）
```

## 文档

完整文档索引见 [docs/00-README.md](docs/00-README.md)，其中：

| 文档 | 内容 |
|------|------|
| [02 系统架构设计](docs/02-系统架构设计.md) | 架构总览、技术栈决策（ADR） |
| [05 知识库与 RAG 详细设计](docs/05-知识库与RAG详细设计.md) | 检索管线、框架选型 |
| [08 安全与多租户设计](docs/08-安全与多租户设计.md) | 租户隔离、认证授权、密域 |
| [15 二期功能交付说明](docs/15-二期功能交付说明.md) | 二期功能与 RAG 阶段交付记录 |
| [16 测试体系](docs/16-测试体系.md) | 测试分层、覆盖清单、编写约定 |
| [17 部署指南](docs/17-部署指南.md) | 容器化构建与编排、环境变量、FAQ |
| [decisions/OPEN-DECISIONS.md](docs/decisions/OPEN-DECISIONS.md) | 架构决策台账 |

## 贡献

欢迎贡献。请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)、[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) 与 [LICENSE](LICENSE)。

提交前请确保本地门禁通过：

```bash
node scripts/check-emoji.js                  # P0：禁止 emoji 作功能图标
node scripts/check-hardcoded-color.js frontend
bash scripts/test-all.sh
```

## 许可

见 [LICENSE](LICENSE)。