# Step 1: Use a verified, modern JDK 17 runtime base image
FROM eclipse-temurin:17-jdk-alpine

# Step 2: Set the isolated application working workspace directory path
WORKDIR /app

# Step 3: Copy the compiled Maven jar file artifact straight into the container environment
COPY target/distributed-crawler-0.0.1-SNAPSHOT.jar app.jar

# Step 4: Expose the standard backend API server gateway entrypoint port
EXPOSE 8081

# Step 5: Define the operational execution runtime command path
ENTRYPOINT ["java", "-jar", "app.jar"]