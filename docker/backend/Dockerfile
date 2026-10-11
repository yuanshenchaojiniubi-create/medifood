# 构建阶段 - 构建所有后端微服务
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制pom文件先下载依赖（利用Docker缓存）
COPY backend/pom.xml backend/pom.xml
RUN mvn dependency:go-offline -pl backend/medi-food-common -am -q

# 复制源码
COPY backend/ ./backend/

# 构建所有微服务（排除common，它是公共依赖）
RUN mvn clean package -DskipTests -pl backend/medi-food-user,backend/medi-food-knowledge,backend/medi-food-recipe,backend/medi-food-community,backend/medi-food-qa,backend/medi-food-ai,backend/medi-food-gateway -am -q


# 基础镜像
FROM eclipse-temurin:17-jre-alpine

# 创建非root用户
RUN addgroup -S medifood && adduser -S medifood -G medifood

# 时区配置
RUN apk add --no-cache tzdata
ENV TZ=Asia/Shanghai

WORKDIR /app

# 复 制 jar 文件到对应目录
COPY --from=builder /app/backend/medi-food-user/target/medi-food-user-1.0.0.jar /app/medi-food-user.jar
COPY --from=builder /app/backend/medi-food-knowledge/target/medi-food-knowledge-1.0.0.jar /app/medi-food-knowledge.jar
COPY --from=builder /app/backend/medi-food-recipe/target/medi-food-recipe-1.0.0.jar /app/medi-food-recipe.jar
COPY --from=builder /app/backend/medi-food-community/target/medi-food-community-1.0.0.jar /app/medi-food-community.jar
COPY --from=builder /app/backend/medi-food-qa/target/medi-food-qa-1.0.0.jar /app/medi-food-qa.jar
COPY --from=builder /app/backend/medi-food-ai/target/medi-food-ai-1.0.0.jar /app/medi-food-ai.jar
COPY --from=builder /app/backend/medi-food-gateway/target/medi-food-gateway-1.0.0.jar /app/medi-food-gateway.jar

# 切换到非root用户
USER medifood

# 暴露端口
# User: 8201, Knowledge: 8202, Recipe: 8203, Community: 8204, QA: 8205, AI: 8206, Gateway: 2020
EXPOSE 8201 8202 8203 8204 8205 8206 2020

CMD ["java", "-jar", "/app/medi-food-gateway.jar"]