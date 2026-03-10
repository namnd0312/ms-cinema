FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY auth-service/target/auth-service.jar auth-service.jar
ENTRYPOINT ["java", "-jar", "auth-service.jar"]
