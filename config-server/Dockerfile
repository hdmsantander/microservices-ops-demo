# =============================================================================
# Spring Cloud Config Server - Container Best Practices
# =============================================================================
FROM eclipse-temurin:21-jre AS builder

WORKDIR /builder

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar

RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted
RUN cp extracted/application/*.jar ./application.jar

# =============================================================================
# Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="config-server" \
      org.opencontainers.image.description="Spring Cloud Config Server" \
      org.opencontainers.image.vendor="microservices-ops-demo"

ENV USER=spring
ENV UID=10001
ENV GID=10001

RUN addgroup -g ${GID} ${USER} && adduser -u ${UID} -G ${USER} -D ${USER}

WORKDIR /application

COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
COPY --from=builder /builder/application.jar ./

RUN chown -R ${USER}:${USER} /application
USER ${USER}

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "application.jar"]

HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=3 \
    CMD-SHELL wget -q --spider http://localhost:8888/actuator/health/readiness || wget -q --spider http://localhost:8888/actuator/health || exit 1
