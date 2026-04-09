FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/auth-service.jar auth-service.jar
ENTRYPOINT ["java", "-jar", "auth-service.jar"]
