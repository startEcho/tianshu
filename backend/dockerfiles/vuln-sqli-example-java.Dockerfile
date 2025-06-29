# Stage 1: Build the application using Maven
FROM eclipse-temurin:17-jdk-jammy as builder
WORKDIR /app

# Copy the entire project context
COPY . .

# Navigate to the specific module and build
# Make sure this path is correct relative to the project root
WORKDIR /app/vulnerable-apps/vuln-sqli-example-java
RUN ../../mvnw clean package -DskipTests # Use mvnw from the root

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the executable JAR from the builder stage
# Adjust the path to the JAR based on the WORKDIR in the builder stage
COPY --from=builder /app/vulnerable-apps/vuln-sqli-example-java/target/vuln-sqli-example-java.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]