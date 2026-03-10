FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/booking-service.jar booking-service.jar
ENTRYPOINT ["java", "-jar", "booking-service.jar"]
