FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/movie-service.jar movie-service.jar
ENTRYPOINT ["java", "-jar", "movie-service.jar"]
