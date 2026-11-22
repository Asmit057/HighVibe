# Builds any HighVibe service (run from backend/): docker build --build-arg MODULE=account-service .
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN ./mvnw -B -q -pl ${MODULE} -am package -DskipTests && cp ${MODULE}/target/${MODULE}-*.jar /app.jar

FROM eclipse-temurin:21-jre
ARG WITH_NODE=false
# workspace-service runs local-mode previews with npm
RUN if [ "$WITH_NODE" = "true" ]; then \
      apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && \
      curl -fsSL https://deb.nodesource.com/setup_22.x | bash - && \
      apt-get install -y --no-install-recommends nodejs && rm -rf /var/lib/apt/lists/*; \
    fi
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
