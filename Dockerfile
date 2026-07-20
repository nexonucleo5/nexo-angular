# syntax=docker/dockerfile:1

# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — build do frontend Angular
# ─────────────────────────────────────────────────────────────────────────────
FROM node:22 AS frontend
WORKDIR /app/nexo
COPY nexo/package.json nexo/package-lock.json ./
RUN npm ci
COPY nexo/ ./
RUN npx ng build --configuration production --base-href /
# saída: /app/nexo/dist/nexo/ (index.html + assets na raiz — angular.json usa browser:"")

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — build do backend Spring, com o Angular embutido como estáticos
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app/backend
COPY backend/pom.xml ./
COPY backend/src ./src
# injeta o build do Angular em resources/static → o Spring serve as telas
COPY --from=frontend /app/nexo/dist/nexo/ ./src/main/resources/static/
RUN mvn -B clean package -DskipTests

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3 — imagem de runtime enxuta (só o JRE + o jar)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /app/backend/target/nexo-backend-0.1.0.jar app.jar
# O Render injeta a porta em $PORT; o app lê server.port=${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
