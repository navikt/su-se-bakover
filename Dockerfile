FROM eclipse-temurin:21-jre

WORKDIR /app
COPY bootstrap/build/libs/*.jar ./

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:InitialRamPercentage=50", "-XX:MaxDirectMemorySize=128m", "-jar", "/app/app.jar"]