FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/config-server.jar config-server.jar
ENTRYPOINT ["java", "-jar", "config-server.jar"]
