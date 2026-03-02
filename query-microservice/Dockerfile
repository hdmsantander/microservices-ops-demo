# =============================================================================
# Builder stage: Extract Spring Boot layers for optimal Docker caching
# =============================================================================
FROM eclipse-temurin:25-jre AS builder

WORKDIR /builder

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar

# Use jarmode=tools (replaces deprecated layertools) to extract layered jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Copy application jar to fixed name for portable ENTRYPOINT (avoid cp same-file error)
RUN cp extracted/application/*.jar ./application.jar

# =============================================================================
# Runtime stage: Minimal production image
# =============================================================================
FROM eclipse-temurin:25-jre-alpine

# Security: Run as non-root user
ENV USER=spring
ENV UID=10001
ENV GID=10001

RUN addgroup -g ${GID} ${USER} && adduser -u ${UID} -G ${USER} -D ${USER}

WORKDIR /application

# Copy layers in order of change frequency (least to most) for optimal caching
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
COPY --from=builder /builder/application.jar ./

# Ensure application files are owned by non-root user
RUN chown -R ${USER}:${USER} /application
USER ${USER}

# JVM tuning for containers: respect memory limits, use container-aware defaults
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

ENV SPRING_PROFILES_ACTIVE=development

# Run the thin application jar (references extracted layers from classpath)
ENTRYPOINT ["java", "-jar", "application.jar"]

# Health check via Spring Boot Actuator (query service runs on 8082)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8082/actuator/health || exit 1
