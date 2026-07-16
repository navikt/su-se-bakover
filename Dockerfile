FROM eclipse-temurin:24-jre

WORKDIR /app
COPY bootstrap/build/libs/*.jar ./

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=65.0", "-XX:MaxDirectMemorySize=512m", "-jar", "/app/app.jar"]