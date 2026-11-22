# syntax=docker/dockerfile:1
# Builds any HighVibe service (run from backend/): docker build --build-arg MODULE=account-service .
# The build stage compiles every module once and is shared by all six service images,
# so Docker Compose runs a single Maven build; the Maven repository is cached between builds.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
ARG WITH_NODE=false
# workspace-service runs local-mode previews with npm
RUN if [ "$WITH_NODE" = "true" ]; then \
      apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && \
      curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
      apt-get install -y --no-install-recommends nodejs && rm -rf /var/lib/apt/lists/*; \
    fi
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-*.jar /app/app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
