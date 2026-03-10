FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/eureka-server.jar eureka-server.jar
ENTRYPOINT ["java", "-jar", "eureka-server.jar"]
