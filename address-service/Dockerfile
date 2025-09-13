FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew ./
COPY build.gradle ./
COPY gradle/ gradle/

# Make gradlew executable
RUN chmod +x ./gradlew

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew build -x test

# Expose port
EXPOSE 8083

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar build/libs/$(ls build/libs/*.jar | grep -v plain | head -1 | xargs basename)"]