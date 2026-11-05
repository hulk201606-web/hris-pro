#!/usr/bin/env node
/**
 * P0 门禁：凭据（明文口令/密钥）扫描。
 *
 * 为什么需要它：2026-10-18 的 `scripts/init_db.py` 曾以大写 `DB_PASSWORD = "..."`
 * 形式硬编码数据库口令并进入 git 历史。当时的临时扫描用了**小写无 -i** 的正则
 * （password|secret|api_key），因此整条漏过——大写形式不匹配。
 * 这个脚本把扫描固化为门禁，核心约束是**大小写不敏感**。
 *
 * 用法：
 *   node scripts/check-secret.js            # 扫描全仓（排除 .gitignore 已忽略项）
 *   node scripts/check-secret.js <目录>     # 扫描指定目录
 *
 * 退出码：0 = 无命中；1 = 命中（CI 应失败）
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');

// 扫描的后缀白名单：覆盖本项目会承载凭据的所有载体
const SCAN_EXT = new Set([
  '.py', '.sh', '.bash', '.yml', '.yaml', '.java', '.ts', '.tsx', '.js',
  '.jsx', '.vue', '.sql', '.conf', '.json', '.properties', '.env', '.toml',
  '.ini', '.xml', '.gradle', '.tf', '.md',
]);

// 扫描时整体跳过的目录
const SKIP_DIR = new Set([
  '.git', 'node_modules', 'target', 'dist', 'build', '.workbuddy', '.tmp',
  '.idea', '.vscode', 'coverage', 'reports', 'surefire-reports',
]);

// 明文凭据特征：**大小写不敏感**（i 标志是本次事故的关键）
//
// 设计说明（每条都来自本项目真实踩过的坑，不是臆测）：
//
// 1) 长度阈值 {4,} 而非 {6,}：事故原文出现过 MYSQL_ROOT_PASSWORD: root（仅 4 字符）。
//    阈值放到 6 会让这类短口令整体漏过，而短口令正是数据库默认弱值的高发区。
//
// 2) 三个分支分别对应三种书写形态：
//      KEY = "value"   KEY: "value"    —— 带引号（Python / JSON / 带引号的 YAML）
//      KEY = 'value'   KEY: 'value'    —— 带单引号
//      KEY: value                       —— **YAML/compose 裸值**，事故原文就是这种形态
//    只匹配带引号会让整个 compose 文件漏检。
//
// 运算符用 \s*[:=]+\s*：可覆盖 `=` / `:` / `:=` / `?=`(Kotlin) / `=>` 等多种赋值形态。
// 只写 [:=] 单字符会漏掉 `:=`，实测 `MY_PASSWORD := "x"` 会被绕过。
//
// 捕获组编号固定为：1=双引号值 2=单引号值 3=YAML 裸值。
// 下游用 m[1] ?? m[2] ?? m[3] 取值，改动正则时务必保持组序。
const SECRET_RE = /(?:password|passwd|pwd|secret|api[_-]?key|access[_-]?key|token)\s*[:=]+\s*(?:"([^"'\n]{4,})"|'([^'\n]{4,})'|([A-Za-z0-9_@#$%.,/\\-]{4,}))/gi;

// 变量引用（${VAR} / $VAR）：不是凭据值
const VAR_REF_RE = /^\$\{[^}]+\}$|^\$[A-Za-z_][A-Za-z0-9_]*$/;

// 纯标识符（变量名传参，如 db_password / password / this.secret）：不是凭据值
const IDENTIFIER_RE = /^[A-Za-z_][A-Za-z0-9_]*$/;

// 「取值表达式」而非字面量：os.environ.get("X")、process.env.X、params.get("secret")。
// 这类右侧不是凭据值本身，而是从环境/请求里取值的调用，必须放过——
// 否则每一个正确写法的环境变量注入都会被误报。
const GETTER_RE = /^[A-Za-z_][A-Za-z0-9_]*(\s*\.\s*[A-Za-z_][A-Za-z0-9_]*)*\s*\(/;

// 点分链（os.environ.get / process.env / System.getenv）：是「从哪里取值」的路径，
// 不是凭据字面量。裸值分支只能捕获到 "os.environ.get" 这一段（不含括号），
// 故需单独识别。
const DOT_CHAIN_RE = /^[A-Za-z_][A-Za-z0-9_]*(\s*\.\s*[A-Za-z_][A-Za-z0-9_]*)+$/;

// 已知弱口令字典：这些值本身没有任何强度，必须命中门禁。
// 与 IDENTIFIER_RE 的判定冲突——root / admin / test 都是纯字母标识符，
// 会被「纯标识符=变量名传参」的规则放过。故显式列出，宁可误报也不漏报。
const WEAK_LITERAL_RE = /^(root|admin|administrator|test|demo|user|pass|passwd|password|secret|token|123456|12345678|qwerty|abc123|letmein|welcome|changeme|default|mysql|postgres|minioadmin)$/i;

// 字典/JSON 字面量形态：{"password": "x"} / {'api_key': 'x'} / `"token":"x"`
// 键名自带引号，与 SECRET_RE 要求的「键名紧邻运算符」形态不同，需单独一轮。
// 这是实测确认的真实绕过点：只写 SECRET_RE 时字典字面量里的凭据会被整条漏掉。
// 捕获组编号与 SECRET_RE 保持一致：1=双引号值 2=单引号值 3=YAML 裸值。
const DICT_KEY_RE = /["'](?:password|passwd|pwd|secret|api[_-]?key|access[_-]?key|token)["']\s*:\s*(?:"([^"'\n]{4,})"|'([^'\n]{4,})'|([A-Za-z0-9_@#$%.,/\\-]{4,}))/gi;

// 命中后仍需排除的合法值：占位符、变量引用、示例、测试用假值
const ALLOW_PATTERNS = [
  /\$\{[^}]+\}/,            // ${VAR} 变量引用
  /^\$?[A-Z_][A-Z0-9_]*$/,  // 全大写常量名占位
  /^(xxx|yyy|zzz|abc123|test|dummy|placeholder|example|sample|changeme|change-me|your[-_]?.*|xxx-.*)$/i,
  /^(TODO|FIXME|REPLACE.*|__.*__|\*+)$/,
  /^[<\[(].*[>\])]$/,        // <password>、[PASSWORD]、(secret) 这类占位
  /^(null|nil|none|true|false|undefined)$/i,
  /\.\.\./,                 // 省略号
  /\*\*\*/,                // 掩码
  // 数据库/中间件的已知默认弱值（root / minioadmin / postgres 等）：本身即高危信号，
  // 出现即须显式改掉，因此**不豁免**，让门禁逼着改成变量注入。
];

// 明确豁免的测试文件：测试里出现固定假口令是合理的
// 但仍会扫描——若假口令长得像真口令（如含 @ 且有强度），测试照样命中。
// 这里的豁免仅针对「测试中显式声明的固定字符串」，由 TEST_ALLOW 精确控制。
const FILE_EXEMPT_PATTERNS = [
  // 单元测试中用于构造异常分支的固定假值（不含真实凭据特征）
  /src\/test\/.*Test\.java$/,
  /src\/test\/.*\.test\.[tj]s$/,
];

/**
 * 逐条登记的精确豁免：`{ 文件匹配, 行内容匹配, 豁免理由 }`
 * 每条都必须写明「为什么它不是凭据」——不允许笼统按目录或按前缀豁免。
 * 这是为了让后来者能逐条复核，而不是继承一个"我知道它无害"的隐性假设。
 */
const LINE_EXEMPT = [
  {
    // 门禁脚本自身：豁免表里写的匹配模式本身就是 password=xxx 形态。
    // 这不是漏洞——这些是「用来识别凭据的正则」，不是凭据本身。
    file: /scripts\/check-secret\.js$/,
    line: /line:\s*\/.*password.*\/|reason:.*凭据|const (SECRET_RE|WEAK_LITERAL_RE)/,
    reason: '门禁脚本自身的检测正则与豁免声明，非凭据',
  },
  {
    file: /frontend\/src\/api\/mock\.ts$/,
    // 前端 mock 数据里的假 token，仅在 VITE_USE_MOCK=true 时使用，
    // 不具备任何真实凭据效力（mock 模式已被标注为开发期特性）
    line: /(mock-access-token|mock-refresh-token)\.v1/,
    reason: '前端 mock 固定假 token，VITE_USE_MOCK 门禁下才生效',
  },
  {
    file: /frontend\/src\/api\/token\.ts$/,
    // localStorage 的键名，不是值本身
    line: /hris\.access_token/,
    reason: 'localStorage 存储键名（非凭据值）',
  },
  {
    file: /hris-auth\/src\/main\/java\/com\/hris\/auth\/util\/TotpUtil\.java$/,
    // TOTP otpauth URI 的参数拼接，secret 由运行时参数传入
    line: /\?secret=" \+ secret \+/,
    reason: 'TOTP URI 拼接，secret 为方法入参而非硬编码字面量',
  },
  {
    file: /README\.md$/,
    // 文档中的生成命令示例，不是真实密钥
    line: /openssl rand -base64 48/,
    reason: '文档中的密钥生成命令示例',
  },
  {
    file: /docs\/.*\.md$/,
    // 交付文档里引用「曾硬编码的弱口令」时的叙述性引用。
    //
    // 两条硬约束（缺一不可）：
    //  1) 用**结构性特征**（凭据字段名 + 占位/弱值），绝不写具体真实口令。
    //     把真实值写进防泄露门禁自身是最坏的循环。
    //  2) 必须被反引号包裹（即文档正文里的内联代码，而非真正的配置行）。
    //     只按「出现凭据字段名」豁免太宽：实测 `DB_PASSWORD = "RealSecret987!"`
    //     放在 docs/ 下会被整条放过，等于给文档开了一个免检后门。
    //     加上反引号 + 占位值双重限定后，真实值仍会被门禁捕获。
    line: /`(?:DB_PASSWORD|MYSQL_ROOT_PASSWORD|MYSQL_PWD|NACOS_AUTH_TOKEN|JWT_SECRET)\s*[=:]\s*(?:root|<[^>]*>|\$\{[^}]+\}|<REDACTED>)`/i,
    reason: '文档正文中反引号包裹的凭据字段引用（占位值/公开弱值），属证据叙述而非凭据',
  },
  {
    file: /\.github\/workflows\/ci\.yml$/,
    // CI 流水线自身的占位凭据：只用于让 compose 变量插值不失败，
    // 值为 ci-placeholder-*，且仓库内不存在真实密钥
    line: /(DB_PASSWORD|JWT_SECRET)=ci-placeholder-[a-z-]+/,
    reason: 'CI 占位凭据（ci-placeholder-*），非真实密钥',
  },
  {
    file: /scripts\/init_db\.py$/,
    // pymysql.connect(password=db_password, ...) —— 右侧是已从环境变量取到的局部变量名
    line: /password=db_password/,
    reason: 'pymysql 连接参数，右侧为环境变量读取结果 db_password（非字面量）',
  },
  {
    file: /scripts\/migrate_cert_no_hash\.py$/,
    // pymysql.connect(password=db_password, ...) 同上：dict 值来自 os.environ.get("DB_PASSWORD")
    line: /password=db_password/,
    reason: 'pymysql 连接参数，右侧为环境变量读取结果 db_password（非字面量）',
  },
  {
    file: /scripts\/seed_e2e\.py$/,
    // pymysql.connect(password=password, ...) 同上
    line: /password=password/,
    reason: 'pymysql 连接参数，右侧为局部变量 password（非字面量）',
  },
  {
    file: /scripts\/verify_feign\.py$/,
    // pymysql.connect(password=DB_PASSWORD, ...) 同上
    line: /password=DB_PASSWORD/,
    reason: 'pymysql 连接参数，右侧为 os.environ 读取结果 DB_PASSWORD（非字面量）',
  },
  {
    // demo 租户的种子账号口令（demo/admin/Admin@123）。
    // 判定为测试夹具而非泄露凭据，依据三条：
    //   1) README.md「默认账号」已公开文档化，属本地联调固定入口；
    //   2) 对应 auth_db 里 tenant=100001 的种子用户（Argon2id 存储），
    //      是 e2e 冒烟脚本的必需参数，不是生产账号；
    //   3) 该口令在 seed_e2e/smoke-e2e/verify_feign/docs 中一致出现，
    //      说明是有意为之的固定测试值。
    // 仍保留门禁提示：若将来 demo 租户被用于真实流量，此条豁免须立即撤销。
    file: /(scripts\/(verify_feign|smoke-e2e|seed_e2e)\.py|README\.md)$/,
    line: /Admin@123/,
    reason: 'demo 租户种子账号固定口令（README 已文档化的本地联调入口，非生产凭据）',
  },
  {
    file: /scripts\/gen_skeleton\.py$/,
    // 骨架生成器输出的 Jinja/占位模板，${{DB_PASSWORD:root}} 是模板变量表达式，
    // 生成的是 application.yml 模板而非可运行的凭据
    line: /\$\{\{DB_PASSWORD:[^}]+\}\}/,
    reason: '骨架生成器的模板占位表达式（${{VAR:default}}），非运行时凭据',
  },
  {
    file: /hris-platform\/src\/main\/resources\/application\.yml$/,
    // ${MINIO_ACCESS_KEY:minioadmin} 是 Spring 的「环境变量:默认值」语法，
    // 左侧已声明 MINIO_ACCESS_KEY 可由环境注入；默认值 minioadmin 是
    // MinIO 官方镜像的公开默认账号，不是本项目自设凭据。
    // 生产由 compose 的 MINIO_ROOT_PASSWORD 覆盖。
    line: /(access-key|secret-key):\s*\$\{(MINIO_ACCESS_KEY|MINIO_SECRET_KEY):[^}]+\}/,
    reason: 'Spring 环境变量占位语法（${VAR:默认值}），值为 MinIO 官方公开默认账号，生产经环境覆盖',
  },
];

function isExemptFile(relPath) {
  return FILE_EXEMPT_PATTERNS.some((re) => re.test(relPath.replace(/\\/g, '/')));
}

function lineExemptReason(relPath, line) {
  for (const rule of LINE_EXEMPT) {
    if (rule.file.test(relPath) && rule.line.test(line)) return rule.reason;
  }
  return null;
}

function isAllowValue(value) {
  const v = value.trim();
  return ALLOW_PATTERNS.some((re) => re.test(v));
}

/**
 * 批量筛出被 .gitignore 忽略的文件。
 *
 * 为什么需要：被忽略的文件永远进不了仓库，扫它只会产生假失败
 * （典型如本地/CI 临时生成的 .env.ci、.env.citest）。
 * 交给 git 判定而不是自己解析 glob，避免与 git 规则不一致导致漏判。
 *
 * 性能：用 `git check-ignore --stdin` **一次**批量判定（实测 837 文件约 0.5s）。
 * 逐文件调用 git 是 0.5s/次 × 837 = 7 分钟，不可接受。
 *
 * @param {string[]} relPaths 相对仓库根的路径列表
 * @returns {Set<string>} 被忽略的路径集合
 */
// git 调用最长等待时间。没有这个上限时，若 git 因 index.lock 争用、
// 凭据弹窗或钩子挂起而卡住，execFileSync 会无限期阻塞 —— CI 上表现为
// 「门禁跑到超时」，等于门禁失效。超时后降级为「不过滤、全量扫描」，
// 宁可慢一点也绝不让门禁静默跳过文件。
const GIT_TIMEOUT_MS = 15000;

function collectIgnored(relPaths) {
  const ignored = new Set();
  if (relPaths.length === 0) return ignored;
  let cp;
  try {
    cp = require('child_process');
    cp.execFileSync('git', ['--version'], { stdio: 'ignore', timeout: GIT_TIMEOUT_MS });
  } catch {
    return ignored; // git 不可用/超时 → 不过滤（保守：继续扫描全部文件）
  }
  try {
    const out = cp.execFileSync(
      'git',
      ['check-ignore', '--stdin', '--no-index'],
      {
        cwd: ROOT,
        input: relPaths.join('\n') + '\n',
        encoding: 'utf8',
        timeout: GIT_TIMEOUT_MS,
        maxBuffer: 64 * 1024 * 1024,
      },
    );
    for (const line of out.split('\n')) {
      const p = line.trim();
      if (p) ignored.add(p);
    }
  } catch (e) {
    // exit 1 = 没有任何文件被忽略（正常）；其他异常 → 不过滤
    const stdout = e && e.stdout ? String(e.stdout) : '';
    for (const line of stdout.split('\n')) {
      const p = line.trim();
      if (p) ignored.add(p);
    }
  }
  return ignored;
}

/** 递归收集待扫描文件（不做 gitignore 过滤，交给 collectIgnored 批量处理） */

function collect(dir, out = []) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (SKIP_DIR.has(e.name)) continue;
      collect(full, out);
    } else if (e.isFile()) {
      const ext = path.extname(e.name).toLowerCase();
      const isDotEnv = e.name === '.env' || e.name.startsWith('.env.');
      if (SCAN_EXT.has(ext) || isDotEnv) {
        out.push(full);
      }
    }
  }
  return out;
}

function scanFile(file) {
  const rel = path.relative(ROOT, file).replace(/\\/g, '/');
  const empty = { hits: [], exempted: [] };
  // .env 是本地凭据文件（已 gitignore），扫描它没有意义（会命中自己的配置）
  if (path.basename(file) === '.env') return empty;
  if (isExemptFile(rel)) return empty;

  let text;
  try {
    text = fs.readFileSync(file, 'utf8');
  } catch {
    return empty;
  }

  const hits = [];
  const exempted = [];
  const lines = text.split(/\r?\n/);
  lines.forEach((line, idx) => {
    const trimmed = line.trim();
    // 整行注释跳过（行首注释符）
    if (trimmed.startsWith('#') || trimmed.startsWith('//') || trimmed.startsWith('*')) return;

    SECRET_RE.lastIndex = 0;
    let m;
    while ((m = SECRET_RE.exec(line)) !== null) {
      // 三个捕获组对应「双引号 / 单引号 / YAML 裸值」三种形态，取第一个非 undefined
      const value = m[1] ?? m[2] ?? m[3];
      if (value === undefined) continue;
      const v = value.trim();

      // 变量引用（${VAR} / $VAR）不是凭据值
      if (VAR_REF_RE.test(v)) continue;
      // 点分链（os.environ.get / process.env / System.getenv）：是「从哪里取值」的路径，
      // 不是凭据字面量。裸值分支只能捕获到 "os.environ.get" 这一段（不含括号），
      // 故需单独识别。
      if (GETTER_RE.test(v)) continue;
      if (DOT_CHAIN_RE.test(v)) continue;
      // 「左变量名 == 右值」的赋值都不是凭据：
      //   this.secret = secret      （Java/TS 字段赋值）
      //   String password = password（同名局部变量）
      //   const token = token        （同名解构）
      // 判据：去掉类型/修饰符后，左侧末段标识符与右侧完全相同。
      const lhsRaw = trimmed.split(/[:=]/)[0] ?? '';
      const lhsKey = (lhsRaw.replace(/^\s*(?:String|const|let|var|final|public|private|protected|static)\s+/i, '')
        .split(/[.=]/).pop() ?? '').trim().toLowerCase();
      if (lhsKey && lhsKey === v.toLowerCase()) continue;
      // 纯标识符（如 password=db_password、this.secret = secret、secret: secret）
      // 是变量名传参而非凭据值，放过。
      // 例外：命中弱口令字典的（root/admin/123456…）本身无强度，必须报出来，
      // 否则「MYSQL_ROOT_PASSWORD: root」这类事故原文会被当成变量名放过。
      if (IDENTIFIER_RE.test(v) && !/\d/.test(v) && !WEAK_LITERAL_RE.test(v)) continue;
      if (isAllowValue(value)) continue;
      const reason = lineExemptReason(rel, line);
      if (reason) {
        exempted.push({ rel, line: idx + 1, reason });
        continue;
      }
      hits.push({ rel, line: idx + 1, text: trimmed.slice(0, 160) });
    }

    // ---- 第二轮：引号包裹的键名形态（JSON / 字典字面量） ----
    // 覆盖 {"password": "x"} / {'api_key': 'x'} —— 上面那轮要求键名紧邻
    // 运算符，字典形态的键名自带引号，会被漏掉。实测是真实绕过点。
    DICT_KEY_RE.lastIndex = 0;
    let dm;
    while ((dm = DICT_KEY_RE.exec(line)) !== null) {
      const val = dm[1] ?? dm[2] ?? dm[3];
      if (val === undefined) continue;
      const v = val.trim();
      if (VAR_REF_RE.test(v) || GETTER_RE.test(v) || DOT_CHAIN_RE.test(v)) continue;
      if (IDENTIFIER_RE.test(v) && !/\d/.test(v) && !WEAK_LITERAL_RE.test(v)) continue;
      if (isAllowValue(val)) continue;
      const reason = lineExemptReason(rel, line);
      if (reason) {
        exempted.push({ rel, line: idx + 1, reason });
        continue;
      }
      hits.push({ rel, line: idx + 1, text: trimmed.slice(0, 160) });
    }
  });
  return { hits, exempted };
}

function main() {
  const arg = process.argv[2] ? path.resolve(process.argv[2]) : ROOT;
  // 支持传入单个文件。原先只按目录处理，传文件会扫描 0 个文件并输出 PASS
  // —— 这是最危险的假绿：用户以为「这个文件扫过了没问题」，实际根本没扫。
  let collected;
  try {
    collected = fs.statSync(arg).isFile() ? [arg] : collect(arg);
  } catch {
    console.error(`[secret] 路径不存在或不可读：${arg}`);
    process.exit(2);
  }
  // 批量剔除被 .gitignore 忽略的文件（一次 git 调用，不逐文件）
  const relAll = collected.map((f) => path.relative(ROOT, f).replace(/\\/g, '/'));
  const ignored = collectIgnored(relAll);
  const files = collected.filter((f) => !ignored.has(path.relative(ROOT, f).replace(/\\/g, '/')));
  const skippedByGitignore = collected.length - files.length;
  const allHits = [];
  const allExempted = [];
  for (const f of files) {
    const r = scanFile(f);
    allHits.push(...r.hits);
    allExempted.push(...r.exempted);
  }

  console.log(
    `扫描 ${files.length} 个文件（后缀白名单 ${SCAN_EXT.size} 类` +
      (skippedByGitignore > 0 ? `，已跳过 ${skippedByGitignore} 个 .gitignore 忽略文件` : '') +
      '）',
  );

  if (allExempted.length > 0) {
    console.log(`\n已按登记理由豁免 ${allExempted.length} 处：`);
    for (const e of allExempted) {
      console.log(`  ${e.rel}:${e.line}  ← ${e.reason}`);
    }
  }

  if (allHits.length === 0) {
    console.log('\nPASS：未发现明文凭据');
    process.exit(0);
  }

  console.log(`\n[secret] 共 ${allHits.length} 处疑似明文凭据：`);
  for (const h of allHits) {
    console.log(`  ${h.rel}:${h.line}  ${h.text}`);
  }
  console.log('\nFAIL：疑似明文凭据，请改为环境变量注入。');
  console.log('  合法用法：password=${DB_PASSWORD} 或 os.environ.get("DB_PASSWORD")');
  console.log('  若确为占位/示例值，请在本脚本 LINE_EXEMPT 中逐条登记理由，不要靠"我知道它无害"。');
  process.exit(1);
}

main();
