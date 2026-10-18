# -*- coding: utf-8 -*-
"""二期代码生成器（Phase 2 增量交付）。

读取 docs/db/schema-mysql.sql 中二期表（rcu_/cmp_/prf_/kb_）的 DDL，
按 hris-attendance 已验证的工程范式，自动产出每个模块的：
  entity / mapper / service / controller / dto
并修正骨架 pom.xml（补齐 aspectj/validation/openfeign/loadbalancer/actuator）
与 application.yml（端口/库名/健康检查）。

约定（与考勤模块一致）：
  - 实体继承 com.hris.common.entity.BaseEntity（id/tenant_id/created_*/updated_*/deleted/version 由基类管理，不重复生成）
  - mapper 继承 BaseMapper
  - service 用 @Service + 构造注入 + @Transactional，R<T>/PageResult
  - controller 用 @RestController + @RequirePerm + 统一前缀 /api/v1/{prefix}
  - DTO 用嵌套 record（Create/Update/Response），@NotNull 仅对 NOT NULL 业务列
  - 公共列之外的所有列按 DDL 精确映射，保证零字段偏差
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SCHEMA = REPO / "docs" / "db" / "schema-mysql.sql"

# mod -> 表前缀 / API前缀 / 端口 / 库名 / 错误码 / 中文描述
MODULES = {
    "recruit":      {"tprefix": "rcu_", "aprefix": "rcu", "port": 8107, "db": "rcu_db", "err": "RCS_ERROR", "desc": "招聘/简历/面试/Offer"},
    "compensation": {"tprefix": "cmp_", "aprefix": "cmp", "port": 8108, "db": "cmp_db", "err": "CMP_ERROR", "desc": "薪酬方案/薪酬项/薪资单/奖金"},
    "performance":  {"tprefix": "prf_", "aprefix": "prf", "port": 8109, "db": "prf_db", "err": "PRF_ERROR", "desc": "考核周期/目标/评分/结果"},
    "rag":          {"tprefix": "kb_",  "aprefix": "kb",  "port": 8110, "db": "rag_db", "err": "RAG_ERROR", "desc": "知识库/文档/切片/问答"},
}

MANAGED = {"id", "tenant_id", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version"}

SQL2JAVA = {
    "BIGINT": "Long",
    "INT": "Integer", "INTEGER": "Integer", "TINYINT": "Integer",
    "SMALLINT": "Integer", "MEDIUMINT": "Integer",
    "VARCHAR": "String", "CHAR": "String", "TEXT": "String",
    "MEDIUMTEXT": "String", "LONGTEXT": "String", "JSON": "String", "ENUM": "String",
    "DATETIME": "LocalDateTime", "TIMESTAMP": "LocalDateTime",
    "DATE": "LocalDate",
    "TIME": "LocalTime",
    "DECIMAL": "BigDecimal", "NUMERIC": "BigDecimal",
    "FLOAT": "Double", "DOUBLE": "Double",
    "BOOLEAN": "Boolean", "BIT": "Boolean",
    "BLOB": "byte[]", "LONGBLOB": "byte[]",
}


def parse_tables(text: str, tprefix: str):
    """返回 [(table, class_name, [(col, jtype, notnull, comment), ...]), ...]"""
    blocks = []
    lines = text.splitlines()
    i = 0
    pat = re.compile(r"CREATE TABLE (?:IF NOT EXISTS )?`?(" + re.escape(tprefix) + r"[\w]+)`?")
    while i < len(lines):
        m = pat.search(lines[i])
        if m:
            table = m.group(1)
            buf = []
            j = i
            while j < len(lines):
                buf.append(lines[j])
                if lines[j].rstrip().endswith(";"):
                    break
                j += 1
            cols = parse_columns("\n".join(buf))
            blocks.append((table, cols))
            i = j + 1
        else:
            i += 1
    return blocks


def parse_columns(block: str):
    cols = []
    col_re = re.compile(r"^\s*`?([\w]+)`?\s+([A-Za-z]+)")
    constraint_kw = {"PRIMARY", "UNIQUE", "KEY", "CONSTRAINT", "FOREIGN", "INDEX",
                     "ENGINE", "CREATE", "TABLE", "FULLTEXT", "SPATIAL"}
    for line in block.splitlines():
        m = col_re.match(line)
        if not m:
            continue
        name = m.group(1)
        base = m.group(2).upper()
        if name.upper() in constraint_kw:
            continue
        jtype = SQL2JAVA.get(base)
        if jtype is None:
            # 非列定义（约束/索引行）或未知类型，跳过
            continue
        notnull = "NOT NULL" in line.upper()
        comment = ""
        cm = re.search(r"COMMENT\s+'([^']*)'", line)
        if cm:
            comment = cm.group(1)
        cols.append((name, jtype, notnull, comment))
    return cols


def is_secret_col(c: str) -> bool:
    """密域列（*_cipher）：仅写入口传入，不进通用响应（薪酬密域下沉 CMP_SALARY_FORBIDDEN）。"""
    return c.endswith("_cipher")


def plural(kebab_name: str) -> str:
    """英文复数规则：-s/-x/-z/-ch/-sh 结尾加 -es，其余加 -s（如 import-batch → import-batches）。"""
    if kebab_name.endswith(("s", "x", "z", "ch", "sh")):
        return kebab_name + "es"
    return kebab_name + "s"


def pascal(name: str, strip_prefix: str) -> str:
    rest = name
    if rest.startswith(strip_prefix):
        rest = rest[len(strip_prefix):]
    parts = [p for p in rest.split("_") if p]
    return "".join(p[:1].upper() + p[1:] for p in parts)


def kebab(class_name: str) -> str:
    s = re.sub(r"(?<!^)(?=[A-Z])", "-", class_name)
    return s.lower()


def camel(s: str) -> str:
    parts = s.split("_")
    return parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])


def field_getter(field: str) -> str:
    return field[:1].upper() + field[1:]


def java_imports(jtypes):
    imp = set()
    if "BigDecimal" in jtypes:
        imp.add("import java.math.BigDecimal;")
    if "LocalDateTime" in jtypes:
        imp.add("import java.time.LocalDateTime;")
    if "LocalDate" in jtypes:
        imp.add("import java.time.LocalDate;")
    if "LocalTime" in jtypes:
        imp.add("import java.time.LocalTime;")
    return "\n".join(sorted(imp))


def gen_entity(mod, pkg, table, cls, cols):
    biz = [(c, t, nn, cm) for (c, t, nn, cm) in cols if c not in MANAGED]
    jtypes = {t for (_, t, _, _) in biz}
    fields = []
    for (c, t, nn, cm) in biz:
        f = camel(c)
        g = field_getter(f)
        desc = f"  // {cm}" if cm else ""
        fields.append(f"    private {t} {f};{desc}\n")
        fields.append(f"\n    public {t} get{g}() {{\n        return {f};\n    }}\n")
        fields.append(f"\n    public void set{g}({t} {f}) {{\n        this.{f} = {f};\n    }}\n")
    tpl = """package $PKG.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hris.common.entity.BaseEntity;
$IMPORTS

/**
 * $COMMENT
 */
@TableName("$TABLE")
public class $CLASS extends BaseEntity {

    private static final long serialVersionUID = 1L;
$FIELDS}
"""
    return tpl.replace("$PKG", pkg).replace("$IMPORTS", java_imports(jtypes)) \
        .replace("$COMMENT", f"{table}（由 DDL 自动生成）") \
        .replace("$TABLE", table).replace("$CLASS", cls) \
        .replace("$FIELDS", "".join(fields))


def gen_mapper(pkg, cls, comment):
    tpl = """package $PKG.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hris.$MOD.entity.$CLASS;
import org.apache.ibatis.annotations.Mapper;

/**
 * $COMMENT 数据访问。
 */
@Mapper
public interface $CLASSMapper extends BaseMapper<$CLASS> {
}
"""
    return tpl.replace("$PKG", pkg).replace("$MOD", pkg.split(".")[-1]) \
        .replace("$CLASS", cls).replace("$COMMENT", comment)


def gen_dto(pkg, cls, cols):
    biz = [(c, t, nn, cm) for (c, t, nn, cm) in cols if c not in MANAGED]
    jtypes = {t for (_, t, _, _) in biz}
    create_params = ", ".join(
        (f'@NotNull(message = "{camel(c)} 不能为空") ' if nn else "") + f"{t} {camel(c)}" for (c, t, nn, cm) in biz
    )
    update_params = ", ".join(f"{t} {camel(c)}" for (c, t, nn, cm) in biz)
    # 密域列（*_cipher）不进通用响应：仅掩码可读，密文走专用密域端点（CMP_SALARY_FORBIDDEN 下沉）
    resp_cols = [(c, t, nn, cm) for (c, t, nn, cm) in biz if not is_secret_col(c)]
    resp_params = "Long id, " + ", ".join(f"{t} {camel(c)}" for (c, t, nn, cm) in resp_cols) + ", LocalDateTime createdAt, LocalDateTime updatedAt"
    # Response 始终含 createdAt/updatedAt（LocalDateTime），必须导入
    imp = set()
    imp.add("import java.time.LocalDateTime;")
    if "LocalDate" in jtypes:
        imp.add("import java.time.LocalDate;")
    if "BigDecimal" in jtypes:
        imp.add("import java.math.BigDecimal;")
    imports = "\n".join(sorted(imp))
    tpl = """package $PKG.dto;

import jakarta.validation.constraints.NotNull;
$IMPORTS

/**
 * $COMMENT DTO（由 DDL 自动生成）。
 */
public final class $CLASSDtos {

    private $CLASSDtos() {
    }

    /** 创建请求。 */
    public record $CLASSCreateRequest($CREATE) {
    }

    /** 更新请求。 */
    public record $CLASSUpdateRequest($UPDATE) {
    }

    /** 响应。 */
    public record $CLASSResponse($RESP) {
    }
}
"""
    return tpl.replace("$PKG", pkg)        .replace("$IMPORTS", imports) \
        .replace("$CLASS", cls).replace("$CREATE", create_params) \
        .replace("$UPDATE", update_params).replace("$RESP", resp_params) \
        .replace("$COMMENT", cls)


def gen_service(pkg, cls, cols, err):
    biz = [(c, t, nn, cm) for (c, t, nn, cm) in cols if c not in MANAGED]
    set_create = "\n".join(f"        e.set{field_getter(camel(c))}(req.{camel(c)}());" for (c, t, nn, cm) in biz)
    set_update = "\n".join(f"        e.set{field_getter(camel(c))}(req.{camel(c)}());" for (c, t, nn, cm) in biz)
    # 密域列（*_cipher）不进 toResponse：与 gen_dto 的 Response 剔除规则保持一致
    resp_args = "e.getId(), " + ", ".join(f"e.get{field_getter(camel(c))}()" for (c, t, nn, cm) in biz if not is_secret_col(c)) + ", e.getCreatedAt(), e.getUpdatedAt()"
    tpl = """package $PKG.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hris.$MOD.dto.$CLASSDtos.$CLASSCreateRequest;
import com.hris.$MOD.dto.$CLASSDtos.$CLASSResponse;
import com.hris.$MOD.dto.$CLASSDtos.$CLASSUpdateRequest;
import com.hris.$MOD.entity.$CLASS;
import com.hris.$MOD.mapper.$CLASSMapper;
import com.hris.common.dto.PageResult;
import com.hris.common.exception.BizException;
import com.hris.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * $COMMENT 服务（CRUD）。
 */
@Service
public class $CLASSService {

    private final $CLASSMapper mapper;

    public $CLASSService($CLASSMapper mapper) {
        this.mapper = mapper;
    }

    public $CLASSResponse get(Long id) {
        $CLASS e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "$CLASS 不存在");
        }
        return toResponse(e);
    }

    public PageResult<$CLASSResponse> list(long page, long size) {
        IPage<$CLASS> pg = mapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<>());
        List<$CLASSResponse> records = pg.getRecords().stream().map(this::toResponse).toList();
        return new PageResult<>(records, page, size, pg.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public $CLASSResponse create($CLASSCreateRequest req) {
        $CLASS e = new $CLASS();
$SETC
        mapper.insert(e);
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public $CLASSResponse update(Long id, $CLASSUpdateRequest req) {
        $CLASS e = mapper.selectById(id);
        if (e == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "$CLASS 不存在");
        }
$SETU
        if (mapper.updateById(e) == 0) {
            throw new BizException(ErrorCode.$ERR, "数据已被其他操作变更，请刷新后重试");
        }
        return toResponse(e);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "$CLASS 不存在");
        }
    }

    private $CLASSResponse toResponse($CLASS e) {
        return new $CLASSResponse($RESP);
    }
}
"""
    modshort = pkg.split(".")[-1]
    return tpl.replace("$PKG", pkg).replace("$MOD", modshort).replace("$CLASS", cls) \
        .replace("$ERR", err).replace("$SETC", set_create).replace("$SETU", set_update) \
        .replace("$RESP", resp_args).replace("$COMMENT", cls)


def gen_controller(pkg, cls, aprefix, entity_path, entity_key, err):
    tpl = """package $PKG.controller;

import com.hris.$MOD.dto.$CLASSDtos.$CLASSCreateRequest;
import com.hris.$MOD.dto.$CLASSDtos.$CLASSResponse;
import com.hris.$MOD.dto.$CLASSDtos.$CLASSUpdateRequest;
import com.hris.$MOD.entity.$CLASS;
import com.hris.$MOD.service.$CLASSService;
import com.hris.common.api.R;
import com.hris.common.dto.PageResult;
import com.hris.common.security.RequirePerm;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * $CLASS 端点。
 */
@RestController
@RequestMapping("/api/v1/$APREFIX/$EPATH")
public class $CLASSController {

    private final $CLASSService service;

    public $CLASSController($CLASSService service) {
        this.service = service;
    }

    @RequirePerm("$APREFIX:$EKEY:read")
    @GetMapping
    public R<PageResult<$CLASSResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(service.list(page, size));
    }

    @RequirePerm("$APREFIX:$EKEY:read")
    @GetMapping("/{id}")
    public R<$CLASSResponse> get(@PathVariable Long id) {
        return R.ok(service.get(id));
    }

    @RequirePerm("$APREFIX:$EKEY:write")
    @PostMapping
    public R<$CLASSResponse> create(@Valid @RequestBody $CLASSCreateRequest req) {
        return R.ok(service.create(req));
    }

    @RequirePerm("$APREFIX:$EKEY:write")
    @PutMapping("/{id}")
    public R<$CLASSResponse> update(@PathVariable Long id, @Valid @RequestBody $CLASSUpdateRequest req) {
        return R.ok(service.update(id, req));
    }

    @RequirePerm("$APREFIX:$EKEY:write")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
"""
    modshort = pkg.split(".")[-1]
    return tpl.replace("$PKG", pkg).replace("$MOD", modshort).replace("$CLASS", cls) \
        .replace("$APREFIX", aprefix).replace("$EPATH", entity_path) \
        .replace("$EKEY", entity_key).replace("$ERR", err)


POM_TPL = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.hris</groupId>
        <artifactId>hris-pro</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>hris-$MOD</artifactId>
    <name>hris-$MOD</name>
    <description>$DESC</description>

    <dependencies>
        <dependency>
            <groupId>com.hris</groupId>
            <artifactId>hris-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aspectj</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
"""

YML_TPL = """server:
  port: $PORT

spring:
  application:
    name: hris-$MOD
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:}
  datasource:
    url: ${DB_URL:jdbc:mysql://127.0.0.1:3306/$DB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}
    driver-class-name: ${DB_DRIVER:com.mysql.cj.jdbc.Driver}

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    banner: false
    db-config:
      id-type: assign_id
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

logging:
  level:
    com.hris: info

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
"""


def main():
    text = SCHEMA.read_text(encoding="utf-8")
    total_files = 0
    for mod, cfg in MODULES.items():
        pkg = "com.hris." + mod
        base = REPO / f"hris-{mod}" / "src" / "main" / "java" / "com" / "hris" / mod
        tables = parse_tables(text, cfg["tprefix"])
        print(f"[{mod}] 解析到 {len(tables)} 张表（前缀 {cfg['tprefix']}）")
        for (table, cols) in tables:
            cls = pascal(table, cfg["tprefix"])
            entity_path = plural(kebab(cls))
            entity_key = kebab(cls)
            comment = table
            (base / "entity").mkdir(parents=True, exist_ok=True)
            (base / "mapper").mkdir(parents=True, exist_ok=True)
            (base / "service").mkdir(parents=True, exist_ok=True)
            (base / "controller").mkdir(parents=True, exist_ok=True)
            (base / "dto").mkdir(parents=True, exist_ok=True)
            write(base / "entity" / f"{cls}.java", gen_entity(mod, pkg, table, cls, cols))
            write(base / "mapper" / f"{cls}Mapper.java", gen_mapper(pkg, cls, comment))
            write(base / "dto" / f"{cls}Dtos.java", gen_dto(pkg, cls, cols))
            write(base / "service" / f"{cls}Service.java", gen_service(pkg, cls, cols, cfg["err"]))
            write(base / "controller" / f"{cls}Controller.java",
                  gen_controller(pkg, cls, cfg["aprefix"], entity_path, entity_key, cfg["err"]))
            total_files += 5
        # 修正 pom / yml
        write(REPO / f"hris-{mod}" / "pom.xml",
              POM_TPL.replace("$MOD", mod).replace("$DESC", cfg["desc"]))
        write(REPO / f"hris-{mod}" / "src" / "main" / "resources" / "application.yml",
              YML_TPL.replace("$MOD", mod).replace("$PORT", str(cfg["port"])).replace("$DB", cfg["db"]))
        print(f"[{mod}] 已修正 pom.xml / application.yml")
    print(f"\n完成：共生成 {total_files} 个 Java 文件（4 个模块）")


def write(path: Path, content: str):
    path.write_text(content, encoding="utf-8")


if __name__ == "__main__":
    main()
