# =====================================================================
# 智云 HR 微服务统一镜像（参数化：一个 Dockerfile 构建 12 个模块）
#
# 用法：
#   docker build -f docker/Dockerfile --build-arg MODULE=hris-gateway -t hris/gateway:1.0.0 .
#   docker build -f docker/Dockerfile --build-arg MODULE=hris-auth    -t hris/auth:1.0.0 .
#   （MODULE 默认 hris-gateway；构建上下文必须是仓库根目录）
#
# 设计要点：
#   1) 多阶段：builder 用 Maven 编译打包，runtime 只带 JRE + 产物，镜像精简且无编译工具链。
#   2) 依赖层缓存：先只拷 pom 做 dependency:go-offline，业务源码变更不会失效依赖层。
#   3) 非 root 运行：容器内建 hris 用户，禁止以 root 跑业务进程。
#   4) 健康检查：走 Actuator liveness，配合 compose 的 depends_on: service_healthy 编排。
#   5) 时区/JVM：固定 Asia/Shanghai；容器内存受限，给出适合 512MB 级别的 JVM 参数。
#      GC 选 G1 + MaxRAMPercentage（不硬编码 -Xmx，避免超出容器 limit 被 OOMKill）。
# =====================================================================

# ---------- 构建参数 ----------
ARG MODULE=hris-gateway
ARG JAVA_VERSION=21
ARG MAVEN_VERSION=3.9

# ---------- 阶段 1：Maven 构建 ----------
FROM maven:${MAVEN_VERSION}-eclipse-temurin-${JAVA_VERSION} AS builder

ARG MODULE
ARG MAVEN_CLI_OPTS="-B -ntp -DskipTests"

WORKDIR /build

# 依赖预热层：仅 pom（依赖父 pom 与各模块 pom），命中缓存时秒级
COPY pom.xml ./
COPY hris-common/pom.xml hris-common/
COPY hris-gateway/pom.xml hris-gateway/
COPY hris-auth/pom.xml hris-auth/
COPY hris-org/pom.xml hris-org/
COPY hris-employee/pom.xml hris-employee/
COPY hris-attendance/pom.xml hris-attendance/
COPY hris-recruit/pom.xml hris-recruit/
COPY hris-compensation/pom.xml hris-compensation/
COPY hris-performance/pom.xml hris-performance/
COPY hris-flow/pom.xml hris-flow/
COPY hris-rag/pom.xml hris-rag/
COPY hris-platform/pom.xml hris-platform/

# 失败也不阻断：依赖预热只是加速手段，离线仓库缺失时应继续走正常下载
RUN mvn ${MAVEN_CLI_OPTS} dependency:go-offline || true

# 源码层
COPY hris-common/src hris-common/src
COPY hris-gateway/src hris-gateway/src
COPY hris-auth/src hris-auth/src
COPY hris-org/src hris-org/src
COPY hris-employee/src hris-employee/src
COPY hris-attendance/src hris-attendance/src
COPY hris-recruit/src hris-recruit/src
COPY hris-compensation/src hris-compensation/src
COPY hris-performance/src hris-performance/src
COPY hris-flow/src hris-flow/src
COPY hris-rag/src hris-rag/src
COPY hris-platform/src hris-platform/src

# 打包目标模块及其依赖（-am 自动带上 hris-common）
RUN mvn ${MAVEN_CLI_OPTS} -pl ${MODULE} -am package

# ---------- 阶段 2：运行时 ----------
FROM eclipse-temurin:${JAVA_VERSION}-jre-jammy AS runtime

ARG MODULE

LABEL org.opencontainers.image.title="hris-${MODULE}" \
      org.opencontainers.image.description="智云 HR 微服务（多租户 SaaS 人力资源系统）" \
      org.opencontainers.image.source="hris-pro"

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=default \
    # 容器内存未知上限时用百分比自适应，避免固定 -Xmx 被 OOMKill
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
-Djava.security.egd=file:/dev/./urandom \
-Dfile.encoding=UTF-8"

WORKDIR /app

# 非 root 用户（Spring Boot 4 / Boot 4 官方推荐做法）
RUN groupadd --system hris && useradd --system --gid hris --home-dir /app --shell /usr/sbin/nologin hris \
 && mkdir -p /app/logs \
 && chown -R hris:hris /app

COPY --from=builder --chown=hris:hris /build/${MODULE}/target/${MODULE}-*.jar /app/app.jar

USER hris
EXPOSE 8080

# Actuator liveness；PORT 由 compose 注入的 SERVER_PORT 决定，故用其兜底 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=20 \
  CMD ["/bin/sh", "-c", "wget -q --spider http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/liveness || exit 1"]

ENTRYPOINT ["/bin/sh", "-c", "exec java ${JAVA_OPTS} -jar /app/app.jar"]