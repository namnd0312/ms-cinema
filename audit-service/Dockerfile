FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/audit-service.jar audit-service.jar
ENTRYPOINT ["java", "-jar", "audit-service.jar"]
