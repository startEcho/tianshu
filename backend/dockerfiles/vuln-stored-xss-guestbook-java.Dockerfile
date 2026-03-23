FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

COPY . .
RUN ./mvnw -pl vulnerable-apps/vuln-stored-xss-guestbook-java -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=builder /app/vulnerable-apps/vuln-stored-xss-guestbook-java/target/vuln-stored-xss-guestbook-java.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
