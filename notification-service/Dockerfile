FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/notification-service.jar notification-service.jar
ENTRYPOINT ["java", "-jar", "notification-service.jar"]
