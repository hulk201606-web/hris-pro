# -*- coding: utf-8 -*-
"""S0.1 工程骨架生成脚本：批量生成 11 个服务的 pom.xml / Application / application.yml。
仅用于脚手架一次性生成，生成后可保留作为参考或删除。"""
import os

ROOT = r"D:/GC_Workspace/hris-pro"
PARENT_VERSION = "1.0.0-SNAPSHOT"

# (模块目录, 包名, Application类名, 端口, 类型, 数据库名, 服务说明)
# 类型: gateway / business / flow / rag
SERVICES = [
    ("hris-gateway",      "gateway",      "GatewayApplication",      8080, "gateway",  None,        "网关：路由/认证前置/租户解析/限流"),
    ("hris-auth",         "auth",         "AuthApplication",         8101, "business", "auth_db",    "认证授权/OAuth2.1/RBAC/租户开通"),
    ("hris-org",          "org",          "OrgApplication",          8102, "business", "org_db",     "组织/法人/部门树/岗位/职级/编制"),
    ("hris-employee",     "employee",     "EmployeeApplication",     8103, "business", "emp_db",     "员工档案/生命周期/合同"),
    ("hris-attendance",   "attendance",   "AttendanceApplication",   8104, "business", "att_db",     "假勤/排班/打卡/月结"),
    ("hris-recruit",      "recruit",      "RecruitApplication",      8105, "business", "rcu_db",     "招聘/简历/面试/Offer"),
    ("hris-compensation", "compensation", "CompensationApplication", 8106, "business", "cmp_db",     "薪酬/算薪/工资条/发薪"),
    ("hris-performance",  "performance",  "PerformanceApplication",  8107, "business", "prf_db",     "绩效/培训/学时"),
    ("hris-flow",         "flow",         "FlowApplication",         8108, "flow",      "flow_db",    "流程中台(Flowable)/待办中心"),
    ("hris-rag",          "rag",          "RagApplication",          8109, "rag",       "rag_db",     "知识库/RAG 智能问答"),
    ("hris-platform",     "platform",     "PlatformApplication",     8110, "business", "plt_db",     "文件/通知/报表/字典/审计"),
]


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("WROTE", os.path.relpath(path, ROOT))


def pom_business(svc, desc):
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.hris</groupId>
        <artifactId>hris-pro</artifactId>
        <version>{PARENT_VERSION}</version>
    </parent>
    <artifactId>{svc}</artifactId>
    <name>{svc}</name>
    <description>{desc}</description>

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
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        </dependency>
        <!-- 分页/租户/乐观锁 inner 插件依赖（3.5.9+ 独立模块） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
        </dependency>
        <!-- 双库驱动：MySQL 8.0 + PostgreSQL 15+（docs/ADR-006） -->
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
'''


def pom_gateway(svc, desc):
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.hris</groupId>
        <artifactId>hris-pro</artifactId>
        <version>{PARENT_VERSION}</version>
    </parent>
    <artifactId>{svc}</artifactId>
    <name>{svc}</name>
    <description>{desc}</description>

    <dependencies>
        <dependency>
            <groupId>com.hris</groupId>
            <artifactId>hris-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
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
'''


def pom_flow(svc, desc):
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.hris</groupId>
        <artifactId>hris-pro</artifactId>
        <version>{PARENT_VERSION}</version>
    </parent>
    <artifactId>{svc}</artifactId>
    <name>{svc}</name>
    <description>{desc}</description>

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
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        </dependency>
        <!-- 分页/租户/乐观锁 inner 插件依赖（3.5.9+ 独立模块） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
        </dependency>
        <!-- Flowable 引擎（Boot4 兼容性为 S0.4 硬门禁，见 docs/04 §8） -->
        <dependency>
            <groupId>org.flowable</groupId>
            <artifactId>flowable-spring-boot-starter-process</artifactId>
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
        <!-- S0.4 引擎装配验证用内存库（仅 test scope） -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
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
'''


def pom_rag(svc, desc):
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.hris</groupId>
        <artifactId>hris-pro</artifactId>
        <version>{PARENT_VERSION}</version>
    </parent>
    <artifactId>{svc}</artifactId>
    <name>{svc}</name>
    <description>{desc}</description>

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
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        </dependency>
        <!-- 分页/租户/乐观锁 inner 插件依赖（3.5.9+ 独立模块） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
        </dependency>
        <!-- Spring AI Alibaba（RAG 管线）：Phase 2/3 按 docs/05 接入具体 starter，骨架阶段先不放依赖 -->
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
'''


POM_GEN = {
    "business": pom_business,
    "gateway": pom_gateway,
    "flow": pom_flow,
    "rag": pom_rag,
}


def gen_application(pkg, cls):
    return f'''package com.hris.{pkg};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {cls} 服务启动类。
 * scanBasePackages 指向 com.hris 根包，以同时扫描 hris-common 公共组件与服务自身。
 */
@SpringBootApplication(scanBasePackages = "com.hris")
public class {cls} {{

    public static void main(String[] args) {{
        SpringApplication.run({cls}.class, args);
    }}
}}
'''


def gen_yml_business(svc, port, db):
    return f'''server:
  port: {port}

spring:
  application:
    name: {svc}
  cloud:
    nacos:
      discovery:
        server-addr: ${{NACOS_ADDR:127.0.0.1:8848}}
        namespace: ${{NACOS_NAMESPACE:}}
  # 数据库连接经环境变量注入，双库二选一（docs/ADR-006）
  datasource:
    url: ${{DB_URL:jdbc:mysql://127.0.0.1:3306/{db}?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=UTC}}
    username: ${{DB_USERNAME:root}}
    password: ${{DB_PASSWORD:root}}
    driver-class-name: ${{DB_DRIVER:com.mysql.cj.jdbc.Driver}}

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
'''


def gen_yml_gateway(svc, port):
    return f'''server:
  port: {port}

spring:
  application:
    name: {svc}
  cloud:
    nacos:
      discovery:
        server-addr: ${{NACOS_ADDR:127.0.0.1:8848}}
        namespace: ${{NACOS_NAMESPACE:}}
    gateway:
      # 路由规则 Phase 3 按服务细化；骨架阶段先保留默认，转发经 Nacos 服务发现
      routes: []
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE

logging:
  level:
    com.hris: info
'''


def gen_yml_flow(svc, port, db):
    return f'''server:
  port: {port}

spring:
  application:
    name: {svc}
  cloud:
    nacos:
      discovery:
        server-addr: ${{NACOS_ADDR:127.0.0.1:8848}}
        namespace: ${{NACOS_NAMESPACE:}}
  datasource:
    url: ${{DB_URL:jdbc:mysql://127.0.0.1:3306/{db}?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=UTC}}
    username: ${{DB_USERNAME:root}}
    password: ${{DB_PASSWORD:root}}
    driver-class-name: ${{DB_DRIVER:com.mysql.cj.jdbc.Driver}}

# Flowable 引擎（S0.4 验证：database-schema-update 用 false 由 DDL 基线管理；验证阶段可用 true 自动建表）
flowable:
  database-schema-update: false
  async-executor-activate: false

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
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
    org.flowable: info
'''


def main():
    for svc, pkg, cls, port, typ, db, desc in SERVICES:
        write(os.path.join(ROOT, svc, "pom.xml"), POM_GEN[typ](svc, desc))
        java = gen_application(pkg, cls)
        write(os.path.join(ROOT, svc, "src", "main", "java", "com", "hris", pkg, f"{cls}.java"), java)
        if typ == "gateway":
            yml = gen_yml_gateway(svc, port)
        elif typ == "flow":
            yml = gen_yml_flow(svc, port, db)
        else:
            yml = gen_yml_business(svc, port, db)
        write(os.path.join(ROOT, svc, "src", "main", "resources", "application.yml"), yml)
    print("DONE")


if __name__ == "__main__":
    main()
