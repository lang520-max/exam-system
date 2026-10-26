# ============================================================
#  高并发在线考试系统 —— 多阶段构建（Spring Boot 3.x + JDK 17）
# ============================================================

# ---------- 阶段一：构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先拷贝 pom.xml 预热依赖（Docker 层缓存优化）
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 拷贝源码并打包
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 阶段二：运行 ----------
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="exam-system" \
      description="高并发在线考试系统：智能组卷 · Redis 倒计时 · WebSocket · RabbitMQ 异步判分"

WORKDIR /app

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 非 root 用户运行
RUN addgroup -S app && adduser -S -G app app

COPY --from=builder /build/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

# 健康检查打 actuator 的 /actuator/health（SecurityConfig 里已放行）。
# 不能用需要鉴权的业务接口：/api/auth/me 无 token 会返回 401，
# curl -f 遇到 4xx 退出码为 22，容器会被永久标记为 unhealthy。
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]