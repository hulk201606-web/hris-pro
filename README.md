# HRIS Pro

[![License](https://img.shields.io/github/license/hulk201606-web/hris-pro)](https://github.com/hulk201606-web/hris-pro/blob/main/LICENSE)
[![Build Status](https://img.shields.io/badge/build-pending-lightgrey)](#)
[![Release](https://img.shields.io/badge/release-0.1.0-blue)](#)

一个面向企业的开源多租户 SaaS 人力资源管理系统（HRIS），基于现代 Spring 与 Vue 技术栈构建，目标支持万人级员工规模与多法人、多租户的生产级部署。

本项目适合希望搭建企业级 HR 系统的开发者、SaaS 产品团队和研究人员，提供流程化审批、知识库+智能问答、以及可扩展的微服务与部署方案。

核心亮点

- 多租户架构与租户隔离设计
- 深度集成 Flowable 工作流（审批流中台）
- 企业知识库 + RAG 智能问答（向量检索：Milvus）
- 支持 MySQL/Postgres，可扩展第三方 HR/工资系统对接
- 开发友好的前端：Vue 3 + TypeScript + Ant Design Vue
- 本地 Docker Compose 到 Kubernetes 的部署迁移路径

技术栈

- 后端：Spring Boot 4、Spring Cloud 2025.1、Spring Cloud Alibaba
- 流程引擎：Flowable 7.2.x
- AI/向量检索：Spring AI、Milvus（向量库）
- 前端：Vue 3、TypeScript、Vite、Ant Design Vue
- 持久层：MySQL / PostgreSQL
- 部署：Docker Compose（开发）、Kubernetes（生产）

快速开始（开发者指南）

先决条件

- Java 17+
- Node.js 18+
- Docker & Docker Compose
- Maven 或 Gradle

克隆仓库

```bash
git clone https://github.com/hulk201606-web/hris-pro.git
cd hris-pro
```

启动依赖服务（示例）

如果仓库包含 docker-compose.yml：

```bash
docker compose up -d
# 等待数据库、Milvus、消息中间件等依赖服务启动
```

后端（示例）

```bash
cd backend
./mvnw clean package
./mvnw spring-boot:run
# 或者使用 IDE 启动主应用
```

前端（示例）

```bash
cd frontend
npm install
npm run dev
```

默认访问

- 前端（Vite）：http://localhost:5173
- 后端 API：http://localhost:8080

文档与目录

- docs/ — 项目文档（架构、部署、模块说明）
- backend/ — 后端服务代码
- frontend/ — 前端代码
- flowable/ — 流程定义与工作流资源（如存在）

贡献指南

欢迎贡献！我们推荐以下流程：

1. Fork 本仓库
2. 新建 feature 分支：git checkout -b feat/your-feature
3. 提交并推送：git commit -m "feat: ..." && git push
4. 提交 Pull Request，描述变更与测试步骤

在贡献前请查看或补充下列文件（如果仓库中已有，请遵守其内容）：

- CONTRIBUTING.md — 贡献流程与代码风格
- CODE_OF_CONDUCT.md — 行为规范
- LICENSE — 版权与许可证

问题与支持

- 提 Issue 报告 Bug 或提交改进建议
- 欢迎讨论设计决策、部署方案与插件对接

许可证

见仓库中的 LICENSE 文件。

路线图（示例）

- v0.1：基础多租户、人事与审批流
- v0.2：知识库与 RAG 问答集成（Milvus）
- v1.0：Kubernetes 生产就绪、自动化备份与高可用方案

联系

仓库：https://github.com/hulk201606-web/hris-pro

---

如果你希望，我可以：
- 为 docs/ 目录生成一个 docs/README.md，作为开发文档入口；
- 添加 CONTRIBUTING.md、CODE_OF_CONDUCT.md 模板；
- 在 README 中加入更详细的环境变量、示例 docker-compose 与 CI 配置。
