# ADR-001: 前后端契约防漂移——契约源唯一化与工具选型

## Status: Accepted (2026-10-18)

> 修订记录：本 ADR 初版建议「openapi.yaml 人工维护为唯一源 + 不引入 springdoc」。
> 该结论基于二手资料（社区博客称 springdoc 泛型会退化为 Object），
> 后经**一手实验推翻**——实测 springdoc 3.1.1 零注解正确解析了 `R<PageResult<X>>` 嵌套泛型。
> 叠加团队实测的覆盖率数据（124/434 ≈ 29%，手写 300+ 端点不可持续），
> 现改为：**代码注解为唯一源，openapi 为生成物**。详见「决策修订依据」。

## Background

hris-pro 一期/二期已全量交付。经实测确认的契约现状：

| 实测项 | 数值 | 口径说明 |
|---|---|---|
| 后端端点总数 | 434 | `@Get/Post/Put/Delete/PatchMapping` 注解计数（按模块：att 68 / prf 82 / cmp 68 / rcu 58 / rag 53 / org 31 / plt 27 / emp 20 / flow 18 / auth 9） |
| openapi.yaml 声明路径 | 124 | 其中 114 个 `/api/v1/*` + 10 个 `/internal/*` |
| **契约覆盖率** | **约 29%** | 124/434，缺口 310 个端点 |
| 契约未覆盖模块 | rcu / cmp / prf / kb | 二期四大业务在契约中完全空白 |
| 前端去重调用 | 51 | 其中 22 个（43%）落在契约未覆盖模块 |
| 契约自身合法性 | 56 error | Redocly 2.64.1：53 缺 security + 3 结构性错误 |
| 手写类型 | api.d.ts + admin.d.ts | 全部手写，后端改字段前端静默失效 |

全仓 pom.xml **无任何 springdoc 依赖**，openapi.yaml 是纯手写产物，无运行时生成器兜底。
这意味着后端改接口时契约不自动更新、CI 不会变红——漂移是静默的
（`generated-code-failure-modes` 所列「沉默逻辑错误」，危险等级最高）。

## 决策

### 决策一：契约源唯一化 = 代码注解为唯一源，openapi.yaml 降级为生成物

**采用 springdoc-openapi 3.1.1 从运行中的 Controller 反向生成契约。**

理由：

1. **手写不可持续（决定性理由）**：310 个未声明端点，其中二期 49 张表经 `gen_phase2.py`
   生成的 CRUD 端点占多数。人工撰写数百个端点的 request/response schema 与 200+ schema
   定义，成本远超收益，且必然滞后于代码。
2. **与既有范式同构**：`gen_phase2.py` 已是「声明式源 → 脚本生成下游」（DDL 是源，生成
   entity/dto/controller）。引入 springdoc 后链路变为
   **DDL → 代码 → openapi → TS 类型**，仍是单向生成，源头唯一，不引入第二个人工维护源。
3. **漂移由构造消除，而非由校验发现**：契约从代码生成，后端改接口时契约自动同步，
   不存在「忘记改契约」这一类漂移。这是手写源方案无法达到的。

### 决策二：工具选型 = springdoc 3.1.1 + openapi-typescript 7.13.0（一次生成、两端消费）

选型对比（openapi-typescript / orval / swagger-typescript-api 均为 npm view 实查版本，
生成行为均在本项目或等价 spec 上实跑验证）：

| 方案 | 版本 | 与 Vue3+TS strict 契合 | 增量生成 | CI 可集成 | 依赖体积 | 侵入 http.ts |
|---|---|---|---|---|---|---|
| **openapi-typescript（选中）** | 7.13.0 | 纯 .d.ts 零运行时，strict 全兼容 | 单文件重生成，diff 干净 | 一条 npm script + git diff | 仅 devDep，零运行时 | 零 |
| orval | 8.43.0 | axios client，与现有 axios 重复 | tags-split 分文件 | 需 config + clean 语义 | devDep + axios 运行时 | 高（重写 7 个 api/*.ts） |
| swagger-typescript-api | 13.13.0 | 命名风格与项目不符 | 单文件 | 可集成 | devDep + 运行时 | 中 |
| springdoc-openapi | 3.1.1 | 不直接产 TS，是上游契约生产者 | 按模块独立生成 | 需聚合 10 模块 | devDep + 运行时反射 | 零（不碰业务代码） |

**串联方式（一次生成、两端消费）**：

```
DDL (schema-mysql.sql)
  └─ gen_phase2.py ─> Controller + DTO (代码，唯一源)
                        └─ springdoc 3.1.1 /v3/api-docs ─> openapi 合并稿
                                                          └─ openapi-typescript 7.13.0
                                                               └─ src/types/generated/api.d.ts
                                                                    └─ 前端 api/*.ts 消费
```

关键验证：**前端类型不是从手写 openapi 来，而是从代码生成的 openapi 来**，
因此后端一改，类型自动跟着变。

**决定性理由（与上一版相同）**：`frontend/src/api/http.ts` 有不可替代的自研逻辑——
401 单飞续期（`refreshPromise` 去重）、`BizError` 错误模型、`traceId` 提取。
orval / swagger-typescript-api 生成的 client 会覆盖这套已调通的容错链路，
收益（省约 30 行）远小于风险。openapi-typescript 只出类型、不碰运行时，是唯一零侵入选项。

**Ant Design Vue 4 不出现在契约层**：表格列定义、表单模型是前端视图关注点，
不被后端 API 约束，两者在 `.vue` 文件里汇合，故无适配问题。

## 决策修订依据（一手实验，推翻初版结论）

初版排除 springdoc 的核心理由是「泛型擦除导致 data 退化为 Object，需 65+ 处
`@ApiResponse` 手工标注」。**该理由经实测不成立**：

实验：搭建 Spring Boot 4.0.6 + springdoc-openapi 3.1.1 最小工程，
**完整复刻项目真实的 `R<T>`（5 字段含 timestamp）与 `PageResult<T>` record**，
以及项目典型签名 `R<X>` / `R<List<X>>` / `R<PageResult<X>>`，**零注解**启动后取 `/v3/api-docs`：

| 项目真实签名 | springdoc 3.1.1 实际产出 | 结论 |
|---|---|---|
| `R<AttGroupVO>` | `RAttGroupVO`，code/message/data/traceId/timestamp 齐全，data 正确 $ref | 正确解析 |
| `R<List<AttGroupVO>>` | `RListAttGroupVO`，items 正确 $ref | 正确解析 |
| `R<PageResult<AttGroupVO>>` | `RPageResultAttGroupVO` → `PageResultAttGroupVO` → `AttGroupVO`，两级 $ref 全链路展开 | **嵌套泛型完全正确** |

同时确认 springdoc 3.1.1 与 Spring Boot 4.0.6 兼容（Maven 编译 + 启动均通过，
`Init duration for springdoc-openapi is: 451 ms`），与官方 FAQ 兼容矩阵（4.0.x → 3.0.x/3.1.x）一致。

**串联实测**：springdoc 产出的 JSON 直接喂 openapi-typescript 7.13.0，49.7ms 生成成功，
`RPageResultAttGroupVO` 正确展开为 `data?: components["schemas"]["PageResultAttGroupVO"]`。
即「一次生成、两端消费」链路已端到端跑通。

**两个实测确认的限制（非阻塞）**：

1. `required` 完全缺失（生成产物 0 处 required，全部字段为 `?`）。
   根因：springdoc 不加注解时不推断 required。影响：类型严格度低于手写类型。
   应对：见「风险」第 2 条。
2. `operationId` 取方法名，`get` 60 次 / `list` 57 次 / `create` 67 次 / `update` 60 次跨模块重名。
   实测影响：**对 types-only 生成无害**——构造含两个 `operationId: get` 的 spec，
   openapi-typescript 正常生成不报错（types 空间按 schema/path 索引，不按 operationId）。
   仅在未来若改用 client 生成模式（orval）时才需处理。

## Consequences

正面：
- 310 个未声明端点可一次性自动补齐，二期 rcu/cmp/prf/kb 不再是契约空白
- 后端改字段 → springdoc 重新生成 → TS 类型同步 → `npm run type-check` 变红（实测 TS2551 拦截字段改名）
- 漂移由构造消除，不依赖人工记得同步
- 手写 types/api.d.ts 与 admin.d.ts 可逐步退役，只保留纯前端视图模型

负面 / 待偿：
- 需在 10 个模块引入 springdoc 依赖（devDep 级别，不进生产镜像亦可，详见落地步骤）
- 10 模块 spec 需聚合为一份（网关已有 10 条 `/api/v1/{前缀}/**` 路由，前缀天然不冲突）
- `required` 缺失导致类型偏松，需按需补 `@Schema(required=true)` 或全局 OpenApiCustomiser
- springdoc 引入运行时反射开销（实测初始化 451ms，需评估对各服务启动时间的影响）

## 明确不做（out-of-scope）

- 不替换 http.ts / 不生成 API client 函数（保留 401 单飞续期与 BizError）
- 不改任何 Controller 业务逻辑（只为补 required 时才加注解，属 Step 4 可选优化）
- 不把生成产物纳入 300 行单文件门禁
- 不做 mock（MSW/faker）生成：现有手写 mock 已可用

## 端到端验证

```bash
# 1. 契约生成（以 hris-attendance 为例）
mvn -pl hris-attendance spring-boot:run &
curl --noproxy '*' http://127.0.0.1:8106/v3/api-docs -o /tmp/att.json

# 2. 前端类型生成
cd frontend && npm run gen:api

# 3. 类型门禁
npm run type-check    # 退出码 0

# 4. 漂移复现（必须变红）
# 后端改任一 DTO 字段 -> 重新 gen:api -> npm run type-check 变红
```

契约侧：`npx redocly lint docs/openapi.yaml` 退出码 0。
