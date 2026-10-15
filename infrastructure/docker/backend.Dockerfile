# Build the Spring Boot gateway.
#
# Two stages so the runtime image contains the JAR and a JRE, not a JDK, a
# Maven cache and the source tree.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency resolution is cached separately from the source copy, so editing a
# controller does not re-download the world.
COPY pom.xml .
COPY packages/mesh-core/pom.xml packages/mesh-core/pom.xml
COPY packages/mesh-simulator/pom.xml packages/mesh-simulator/pom.xml
COPY services/backend/pom.xml services/backend/pom.xml
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY packages/mesh-core/src packages/mesh-core/src
COPY services/backend/src services/backend/src
RUN mvn -B -q -pl packages/mesh-core,services/backend -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Never run the gateway as root: a container escape should not land on a
# privileged process.
RUN addgroup -S resqnet && adduser -S resqnet -G resqnet
USER resqnet

COPY --from=build /build/services/backend/target/backend-*.jar /app/gateway.jar

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

HEALTHCHECK --interval=15s --timeout=5s --retries=6 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/gateway.jar"]
