# Stage 1: Build the application using Maven
FROM eclipse-temurin:17-jdk-jammy as builder
WORKDIR /build_workspace

# Copy the entire project context (from tian-shu-platform root)
COPY . .

# Package the requested service together with its reactor dependencies.
RUN ./mvnw package -pl platform-services/lab-orchestration-service -am -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the executable JAR from the builder stage
COPY --from=builder /build_workspace/platform-services/lab-orchestration-service/target/lab-orchestration-service.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
