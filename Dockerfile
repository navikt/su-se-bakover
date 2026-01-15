FROM eclipse-temurin:21-jre

WORKDIR /app
COPY bootstrap/build/libs/*.jar ./

ENTRYPOINT ["java", "-jar", "/app/app.jar"]