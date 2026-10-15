# HRIS Pro

基于 Spring Boot 4 + Spring Cloud 2025.1 + Spring Cloud Alibaba 全家桶、Vue 3 + TypeScript + Ant Design Vue 的多租户 SaaS 人力资源管理系统（HRIS）。

描述
- 深度集成 Flowable 7.2.x 工作流引擎（独立流程中台服务，统一审批中心）。
- 企业知识库 + RAG 智能问答（Spring AI + Milvus 向量库）。
- 设计支持万人级员工、多法人多租户、高可用可容灾部署（支持 Docker Compose 起步、Kubernetes 生产部署）。

主要特性
- 多租户 tenant-isolation 与租户管理
- 人事基础信息管理（员工、组织、岗位、合同）
- 考勤、假期与排班管理
- 薪资与社保核算（可扩展第三方对接）
- 审批流与流程引擎集成（Flowable）
- 企业知识库与智能问答（向量检索 + RAG）
- 权限与审计、日志

技术栈
- 后端：Spring Boot 4、Spring Cloud 2025.1、Spring Cloud Alibaba
- 流程：Flowable 7.2.x
- AI/向量检索：Spring AI、Milvus
- 前端：Vue 3、TypeScript、Vite、Ant Design Vue
- 数据库：支持 MySQL、Postgres（视部署配置）
- 部署：Docker Compose、Kubernetes（建议生产使用 K8s）

快速开始（本地开发）
1. 环境要求
   - JDK 17+
   - Node.js 18+
   - Docker & Docker Compose（用于依赖服务如数据库、Milvus、消息中间件）

2. 拉取代码
   git clone https://github.com/hulk201606-web/hris-pro.git

3. 启动依赖服务（示例：Docker Compose）
   - 在仓库根目录下（如提供 docker-compose.yml）执行：
     docker compose up -d
   - 等待数据库、消息队列、Milvus 等服务就绪

4. 后端运行
   - 进入 backend 服务目录，例如：
     cd backend
   - 使用 Maven/Gradle 构建并运行：
     ./mvnw spring-boot:run
   - 或使用 IDE 运行主类

5. 前端运行
   - 进入 frontend 项目目录，例如：
     cd frontend
   - 安装依赖并启动：
     npm install
     npm run dev

6. 访问
   - 前端：默认 http://localhost:5173（根据 Vite 配置）
   - 后端 API：默认 http://localhost:8080

生产部署建议
- 使用 Kubernetes（带 ingress、证书管理、持久化存储、备份策略）与多副本、高可用组件。
- 使用外部托管的数据库或主从/云数据库以保证可靠性。

文档
- 项目中包含的 docs/ 目录（若有）存放架构设计、部署说明与开发者指南。
- 流程与审批规则请参阅 flowable/ 或流程服务模块的 README

贡献
- 欢迎提交 Issue 或 Pull Request。请在贡献前阅读 CONTRIBUTING.md（若存在）。

许可证
- 请在仓库中查看 LICENSE 文件以获取许可信息。

联系方式
- 仓库地址：https://github.com/hulk201606-web/hris-pro
