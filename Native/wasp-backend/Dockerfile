# Use lightweight Java runtime
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy jar into container
COPY target/wasp-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose container port
EXPOSE 8080

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]

