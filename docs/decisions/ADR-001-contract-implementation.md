# 契约防漂移落地步骤（ADR-001 配套，v2 代码生成版）

每步验收标准均为 CI 机器可判定（退出码），无需人工目视。

## Step 0 — 前置事实核对（不改代码）

**做什么**

确认 10 个模块的实际端点数（我已实测，供执行者交叉核对）：

| 模块 | 端点数 | 模块 | 端点数 |
|---|---|---|---|
| hris-performance | 82 | hris-platform | 27 |
| hris-attendance | 68 | hris-employee | 20 |
| hris-compensation | 68 | hris-flow | 18 |
| hris-recruit | 58 | hris-org | 31 |
| hris-rag | 53 | hris-auth | 9 |

合计 434。openapi.yaml 现声明 124（含 10 个 `/internal/*`），覆盖率约 29%。

**验收标准**：计数命令输出与上表一致（允许 ±5% 因注解写法差异）。

```bash
grep -rhoE "@(Get|Post|Put|Delete|Patch)Mapping" --include=*.java hris-*/src/main | wc -l
```

## Step 1 — 引入 springdoc（10 模块，test/optional 作用域）

**做什么**

父 pom 的 `dependencyManagement` 引入 BOM 版本，各模块加依赖。

```xml
<properties>
  <springdoc.version>3.1.1</springdoc.version>
</properties>
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
      <version>${springdoc.version}</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

各业务模块（auth/org/emp/flow/platform/att/rcu/cmp/prf/kb）加：

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
  <!-- scope 用 provided/optional，生产镜像不打包 -->
</dependency>
```

**为什么用 `-api` 而不是 `-ui`**：`-api` 只暴露 `/v3/api-docs`（契约生成用），
不含 swagger-ui 静态资源。前端不需要 Swagger UI，本项目要的是契约而非文档页面。

**必须同步**：各模块 `application.yml` 关闭生产环境暴露（复用现有 profile 结构）：

```yaml
springdoc:
  api-docs:
    enabled: ${SPRINGDOC_ENABLED:false}   # 本地/CI 开，生产默认关
```

**验收标准**

```bash
mvn -B -ntp -DskipTests package        # 10 模块编译通过
# 本地起一个服务验证
java -jar hris-attendance/target/hris-attendance-*.jar --springdoc.api-docs.enabled=true --server.port=8106
curl --noproxy '*' http://127.0.0.1:8106/v3/api-docs -o /tmp/att.json   # HTTP 200
python -c "import json;d=json.load(open('/tmp/att.json'));print(len(d['paths']))"  # >0
```

**注意**：springdoc 3.1.1 对应 Spring Boot 4（官方 FAQ 兼容矩阵 4.0.x → 3.0.x/3.1.x），
与本项目 Boot 4.0.6 匹配。已实测编译 + 启动通过。

## Step 2 — 契约聚合（10 模块 → 1 份）

**做什么**

新增 `scripts/gen-openapi.sh`：逐模块启停（复用 `scripts/run-one.sh` 的启动方式），
拉 `/v3/api-docs`，合并为 `docs/openapi.gen.json`。

**为什么能直接合并**：网关路由是 10 条 `/api/v1/{auth,org,emp,flow,plt,att,rcu,cmp,prf,kb}/**`
（前缀互不重叠），各 Controller 自带完整前缀、无 StripPrefix（已在
`hris-gateway/src/main/resources/application.yml` 注释中说明）。因此 path 级无冲突。

**去重规则**：`components.schemas` 按 name 合并；同名冲突时加模块前缀重命名（预期为 0，
因各模块 VO 类名带业务前缀，但必须校验并报错而非静默覆盖）。

**验收标准**

```bash
bash scripts/gen-openapi.sh            # 退出码 0
python scripts/check-openapi-coverage.py   # 退出码 0
```

`check-openapi-coverage.py` 校验三项（这是替代「人工核对覆盖率」的关键门禁）：
1. 合并后 path 数 >= 400（当前代码 434，考虑 `/internal` 与重复路径，留 8% 余量）
2. `components.schemas` 无同名冲突
3. 合并后 `paths` 中每个 path 的 prefix 属于 10 个已知前缀之一

## Step 3 — 前端接入类型生成（零业务侵入）

**做什么**

frontend/package.json：

```json
"scripts": {
  "gen:api": "openapi-typescript ../docs/openapi.gen.json -o src/types/generated/api.d.ts",
  "type-check": "vue-tsc --noEmit"
},
"devDependencies": {
  "openapi-typescript": "7.13.0"
}
```

版本必须锁 `7.13.0` 不用 `^`：实测两次生成字节一致，浮动版本会让 CI diff 门禁随机变红。

产物加文件头注释（禁止手工编辑）：

```
// 由 npm run gen:api 自动生成，请勿手工编辑。
// 源：docs/openapi.gen.json（由 springdoc 从 Controller 生成）
```

**验收标准**

```bash
cd frontend && npm ci && npm run gen:api
test -f src/types/generated/api.d.ts
node scripts/check-emoji.js          # 产物不得含 emoji
```

## Step 4 — CI 漂移门禁（核心）

**做什么**

`.github/workflows/ci.yml` 两处修改：

**(a) frontend job** 在 `npm run type-check` 之前插入：

```yaml
- name: Generate API types from generated contract
  run: npm run gen:api

- name: Contract drift gate
  run: git diff --exit-code -- src/types/generated/api.d.ts

- name: Type check (vue-tsc, 0 error 门禁)
  run: npm run type-check
```

**(b) gates job 的 300 行门禁必须排除生成产物**，否则必然误爆：

```bash
# 在现有 find 中增加排除
-not -path '*/types/generated/*'
```

**验收标准**

- 手工改一行 `api.d.ts` → CI 在 "Contract drift gate" 变红
- 不改任何文件 → CI 全绿（幂等，无随机失败）

## Step 5 — 手写类型处置（**已实测修正原计划**）

**原计划**：把 `frontend/src/types/api*.d.ts` 中与后端 DTO 对应的类型改为
从生成物再导出，例如：

```ts
import type { components } from './generated/api';
export type AttGroupVO = components['schemas']['AttGroupVO'];
```

**实测后该计划不成立，改为「保留手写 + 机械校验一致性」**。两个实测依据：

1. **生成类型是按端点具体化的，不存在可直接映射的泛型 schema。**
   springdoc 对 `R<AdjustmentResponse>` 生成的是独立 schema
   `AttRAdjustmentResponse`，而不是可复用的 `R<T>`。因此手写类型里的
   `ApiEnvelope<T>`、`PageResult<T>` 这两个**泛型**在契约中根本没有对应物，
   无法写成 `components['schemas'][...]`。同理，`AttGroup` 对应的契约名是
   `AttAttGroup`（模块前缀 + 业务名），逐个手工映射 132 个类型既易错又无收益。

2. **手写类型与契约当前已完全对齐。**
   用 `scripts/check-type-sync.py` 实测 29 个手写类型：23 个字段完全一致、
   6 个是纯前端视图模型（`CrudColumn`、`MenuNode`、`SelfHomeSummary` 等，
   本就不该出现在契约中）、**0 个字段冲突**。也就是说当前不存在需要
   "修"的漂移，全量替换的收益仅为消除重复定义，而代价是改动 56 个调用点
   并丢失手写类型的 JSDoc 注释。

**因此本步骤实际交付**：`scripts/check-type-sync.py`

- 把手写类型 X 匹配到契约 schema `{模块前缀}X`，比对字段集合
- 报出「契约有但前端缺」（运行时读到 `undefined`）与
  「前端有但契约无」（可能已废弃或拼错）两个方向的差异
- 纯前端视图模型登记在 `FRONTEND_ONLY` 白名单；
  命名与后端不一致的历史遗留登记在 `ALIAS`（如 `FixRequest` ↔
  `AttFixResponse`——这三个考勤业务单前端命名为 Request，但实际用作响应体，
  可从 `get<PageResult<FixRequest>>` 的用法确认）
- **已做反向验证**：给 `EmploymentInfo` 注入一个契约中不存在的字段，
  脚本退出码 1 并精确指出该字段

**为什么这比全量替换好**：防漂移的目标是"后端改字段时前端能被发现"，
机械校验同样能达到，且不需要改动任何业务代码、不丢失注释。
若将来手写类型膨胀到难以维护，再考虑按模块分批替换。

**验收标准**

```bash
python scripts/check-type-sync.py --strict   # 退出码 0
npm run type-check                            # 退出码 0
```

## Step 6（可选）— 收紧 required

**做什么**

实测发现 springdoc 不加注解时不推断 required（生成产物 0 处 required，全字段为 `?`）。
若前端发现类型过松影响重构安全性，有两种补法：

- 局部：易变 DTO 上加 `@Schema(requiredMode = REQUIRED)`
- 全局：写一个 `OpenApiCustomiser` Bean，统一处理

**验收标准**

```bash
npm run gen:api && grep -c "required" src/types/generated/api.d.ts   # >0
```

**建议**：本轮不做。先让契约覆盖率达 100%（Step 2），required 收紧是可后续独立优化的增量项。
过早收紧会引发「契约变严 → 前端 type-check 批量变红」的返工。

## 版本锁定清单

| 组件 | 版本 | 依据 |
|---|---|---|
| springdoc-openapi-starter-webmvc-api | 3.1.1 | GitHub Releases v3.1.1 (2026-09-06) + Maven Central 版本页；对应 Spring Boot 4 |
| openapi-typescript | 7.13.0 | npm view 实查；精确锁，`^` 会导致 CI diff 随机变红 |
| @redocly/cli | 2.64.1 | npm view 实查；契约 lint |
| Node | >=20.19.0 | openapi-typescript 7.x 要求 20+ |

不采用：orval 8.43.0（覆盖 http.ts 的 401 单飞续期与 BizError）、
swagger-typescript-api 13.13.0（命名风格不符）。

## 已知限制（实测确认，非阻塞）

1. **required 缺失**：生成类型全部字段可选，严格度低于手写类型。Step 6 可选处理。
2. **operationId 跨模块重名**：`get` 60 次 / `list` 57 次 / `create` 67 次 / `update` 60 次。
   实测对 types-only 生成**无害**（openapi-typescript 不按 operationId 建索引，
   构造双 `operationId: get` 的 spec 仍正常生成）。仅若未来改用 client 生成模式才需处理。
3. **springdoc 运行时开销**：实测初始化 451ms。需在各服务启动时间基线中观察，
   若影响容器健康探针起始时间，改为按需懒加载（`springdoc.api-docs.enabled` 已在 Step 1 默认关）。

## 端到端验证（成功流 + 错误流）

```bash
# 成功流
bash scripts/gen-openapi.sh
cd frontend && npm ci && npm run gen:api && npm run type-check && npm run build

# 错误流（必须被拦截）
# 1. 后端改 DTO 字段名 -> gen:api -> type-check 变红（TS2551）
# 2. 手工改 src/types/generated/api.d.ts -> CI drift gate 变红
# 3. 某模块 /v3/api-docs 拉不到 -> check-openapi-coverage.py 变红（path 数不足 400）
```
