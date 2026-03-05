# =============================================================================
# Spring Cloud Container Best Practices
# - Multi-stage build with layer extraction for optimal caching
# - Non-root user, minimal base, JVM container tuning
# - Readiness probe for dependency ordering
# =============================================================================
FROM eclipse-temurin:21-jre AS builder

WORKDIR /builder

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar

# Extract Spring Boot layers (dependencies, loader, snapshot-deps, application)
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Thin JAR for runtime (references extracted layers)
RUN cp extracted/application/*.jar ./application.jar

# =============================================================================
# Runtime: Minimal production image
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

# OCI labels for maintainability
LABEL org.opencontainers.image.title="inventory-microservice" \
      org.opencontainers.image.description="Pet Shop Inventory microservice" \
      org.opencontainers.image.vendor="microservices-ops-demo"

# Non-root user (security best practice)
ENV USER=spring
ENV UID=10001
ENV GID=10001

RUN addgroup -g ${GID} ${USER} && adduser -u ${UID} -G ${USER} -D ${USER}

WORKDIR /application

# Copy layers: least to most frequently changing (optimal cache reuse)
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
COPY --from=builder /builder/application.jar ./

RUN chown -R ${USER}:${USER} /application
USER ${USER}

# JVM: container-aware, respect memory limits
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Graceful shutdown: Spring Boot handles SIGTERM; ensure sufficient stop_grace_period in compose
ENV SPRING_PROFILES_ACTIVE=development

# Exec form: ensures Java receives SIGTERM for graceful shutdown
ENTRYPOINT ["java", "-jar", "application.jar"]

# Readiness: use /actuator/health/readiness (Spring Boot 4) for dependency ordering
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=3 \
    CMD-SHELL wget -q --spider http://localhost:8085/actuator/health/readiness || wget -q --spider http://localhost:8085/actuator/health || exit 1
