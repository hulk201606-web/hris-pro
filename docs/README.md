# 项目文档

此目录包含项目的开发文档、部署说明与模块介绍，面向希望参与开发或部署 HRIS Pro 的贡献者与运维同学。

- docs/README.md：文档入口（当前文件）。
- docs/deployment.md：部署指南（Kubernetes / Docker Compose）。
- docs/architecture.md：架构与模块说明。

快速导航

- 开发者请先阅读 CONTRIBUTING.md 以了解代码风格与提交规范。
- 如需报告安全问题，请优先通过 Issue 或联系仓库维护者（见仓库主页）。

本地开发（简要）

1. 环境准备
   - Java 17+
   - Node.js 18+
   - Docker & Docker Compose
   - Maven / Gradle

2. 启动依赖服务（示例）
   - 在仓库根目录：

```bash
cp docker-compose.example.yml docker-compose.yml
# 编辑环境变量（.env）并启动
docker compose up -d
```

3. 后端启动

```bash
cd backend
./mvnw clean package
./mvnw spring-boot:run
```

4. 前端启动

```bash
cd frontend
npm install
npm run dev
```

更多详细部署与架构说明见 docs/deployment.md 与 docs/architecture.md（当前为占位，欢迎贡献具体内容）。
