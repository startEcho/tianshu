# ======================================================================
# STAGE 1: Builder for Java 8 / Spring Boot 2.x Application
# 我们特意为此模块使用一个包含Java 8 JDK的构建镜像，
# 以确保它的编译和打包完全在其期望的环境中进行。
# ======================================================================
FROM eclipse-temurin:8-jdk-jammy as java8_builder
WORKDIR /build_workspace

# 我们仍然复制整个项目上下文，这样Maven才能找到所有的父POM和模块。
COPY . .

# 关键修改：我们运行一个精确的、只针对这个模块的构建命令。
# Maven会使用这个模块内部的pom.xml，该pom指向Spring Boot 2的父项目，
# 并使用与之兼容的依赖和插件版本（包括spring-boot-maven-plugin:2.7.18）。
# 这完全避免了与平台主体的Java 17/Spring Boot 3环境的任何冲突。
RUN ./mvnw clean package -f vulnerable-apps/vuln-deserialize-cc-app/pom.xml -DskipTests


# ======================================================================
# STAGE 2: Final Runtime Image
# 这个阶段创建最终的、轻量级的运行时镜像。
# ======================================================================
FROM eclipse-temurin:8-jre-jammy
WORKDIR /app

# 从我们专属的 `java8_builder` 阶段复制生成的JAR文件。
COPY --from=java8_builder /build_workspace/vulnerable-apps/vuln-deserialize-cc-app/target/vuln-deserialize-cc-app.jar app.jar

EXPOSE 8081

# 在纯粹的Java 8环境下运行，不再需要任何 --add-opens 参数。
ENTRYPOINT ["java", "-jar", "app.jar"]