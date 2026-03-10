FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/payment-service.jar payment-service.jar
ENTRYPOINT ["java", "-jar", "payment-service.jar"]
