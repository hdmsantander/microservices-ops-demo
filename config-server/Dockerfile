FROM eclipse-temurin:21-jre-alpine
WORKDIR /application
COPY target/*.jar application.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "application.jar"]
