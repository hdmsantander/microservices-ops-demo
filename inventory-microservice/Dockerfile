# Builder environment
FROM adoptopenjdk:11-jre-hotspot as builder

WORKDIR /application

ARG JAR_FILE=target/*.jar

COPY ${JAR_FILE} application.jar

RUN java -Djarmode=layertools -jar application.jar extract

# Run environment
FROM amazoncorretto:11-alpine

ENV SPRING_PROFILES_ACTIVE=development

ENV USER spring
ENV UID 1001
ENV HOME /home/$USER

WORKDIR $HOME

COPY --from=builder /application/dependencies/ ./
COPY --from=builder /application/spring-boot-loader/ ./
COPY --from=builder /application/snapshot-dependencies/ ./
COPY --from=builder /application/application/ ./

ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]