FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests
ENTRYPOINT ["sh", "-c", "java -jar target/*.jar"]