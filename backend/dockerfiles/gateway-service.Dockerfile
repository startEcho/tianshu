# Stage 1: Build the application using Maven
FROM eclipse-temurin:17-jdk-jammy as builder
WORKDIR /build_workspace

COPY . .

RUN ./mvnw package -pl platform-services/gateway-service -am -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=builder /build_workspace/platform-services/gateway-service/target/gateway-service.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
