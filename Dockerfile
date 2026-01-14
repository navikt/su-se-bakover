FROM eclipse-temurin:21-jre

COPY bootstrap/build/libs/*.jar ./

ENTRYPOINT ["java", "-jar", "/app/app.jar"]