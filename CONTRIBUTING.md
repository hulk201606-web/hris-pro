# 贡献指南

感谢你愿意为 HRIS Pro 贡献代码、文档或其他改进！为提高协作效率，请遵循以下流程与规范。

快速开始

1. Fork 本仓库到你的账户
2. 新建分支：git checkout -b feat/your-feature
3. 本地实现并编写测试
4. 提交变更：遵循提交信息格式（参见下文）
5. 推送并发起 Pull Request，描述你的变更和测试步骤

分支与提交规范

- 分支命名：feat/<描述>、fix/<描述>、chore/<描述>
- 提交信息示例：
  - feat: add employee import
  - fix: correct salary calc in payroll service
  - chore: update README

代码风格与校验

- 后端（Java）使用统一的格式化规则（建议使用 IDE 的 formatter 或 google-java-format）
- 前端（TypeScript / Vue）使用 ESLint + Prettier，提交前请运行 lint 与格式化
- 提交前请运行相关单元测试

测试

- 请为关键路径编写单元测试与集成测试
- CI 会在 PR 中运行测试与静态检查，请确保本地通过

Pull Request 说明

- 在 PR 描述中说明：变更目的、实现方式、回归测试步骤以及相关 issue（若有）
- 小的功能或文档修正可以由项目维护者合并；较大变更可能需要讨论

代码审查

- 维护者会对 PR 进行代码审查，请根据 Review 意见修改并重新提交

版权与许可

- 贡献即表示你同意以仓库 LICENSE（见 LICENSE 文件）所列许可方式发布你的贡献

感谢你的贡献！