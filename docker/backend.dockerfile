# Use lightweight Java runtime
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the built backend jar (version-agnostic)
COPY wasp-backend/target/wasp-backend-*.jar app.jar

# Expose container port
EXPOSE 8080

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
