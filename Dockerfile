FROM gcr.io/distroless/java25-debian13:nonroot

WORKDIR /app
COPY bootstrap/build/libs/*.jar ./

ENTRYPOINT ["/usr/bin/java", "-XX:-UseBASE64Intrinsics", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=65.0", "-XX:MaxDirectMemorySize=512m", "-jar", "/app/app.jar"]