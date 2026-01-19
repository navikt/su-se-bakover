FROM eclipse-temurin:25-jre

WORKDIR /app
COPY bootstrap/build/libs/*.jar ./

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=50.0", "-XX:MaxDirectMemorySize=128m", "-jar", "/app/app.jar"]