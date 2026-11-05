FROM node:22-alpine AS frontend-build

WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS application-build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src/ ./src/
COPY --from=frontend-build /workspace/src/main/resources/static/ ./src/main/resources/static/
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app
ENV PORT=8080
EXPOSE 8080

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --no-create-home app \
    && mkdir -p /app/data/resumes \
    && chown -R app:app /app
COPY --from=application-build --chown=app:app /workspace/target/recruitment-placement-management-system-0.0.1-SNAPSHOT.jar /app/app.jar

USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
