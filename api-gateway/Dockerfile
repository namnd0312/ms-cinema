FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/api-gateway.jar api-gateway.jar
ENTRYPOINT ["java", "-jar", "api-gateway.jar"]
