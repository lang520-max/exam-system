# ============================================================
#  在线考试 / 题库系统 —— 多阶段构建
#  说明：本机未安装 Docker，此文件仅作为交付物与部署示例，
#       实际构建命令见 README「Docker 部署」小节。
# ============================================================

# ---------- 阶段一：构建 ----------
# 使用带 Maven 的 JDK 8 镜像编译，产物为可执行 fat jar
FROM maven:3.9-eclipse-temurin-8 AS builder

WORKDIR /build

# 先只拷贝 pom.xml 并预热依赖，充分利用 Docker 层缓存：
# 只要 pom.xml 没变，后续改 Java 代码不会重新下载依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再拷贝源码并打包（镜像内构建跳过测试，测试在 CI 的 mvn test 阶段执行）
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- 阶段二：运行 ----------
# 运行阶段只要 JRE，镜像体积从 ~500MB 降到 ~180MB
FROM eclipse-temurin:8-jre-alpine

LABEL maintainer="蔡开浪" \
      description="在线考试/题库系统：教师组卷、学生答题、自动判分与成绩统计"

WORKDIR /app

# 时区与字符集：容器内默认 UTC + POSIX，会让考试开始/交卷时间差 8 小时
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 以非 root 用户运行，降低容器逃逸风险
RUN addgroup -S app && adduser -S -G app app

COPY --from=builder /build/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

# 健康检查：探活 actuator 未引入，这里用登录页返回 200 作为存活信号
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/login || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
