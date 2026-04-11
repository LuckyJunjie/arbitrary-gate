# ============================================================
# Backend Dockerfile - Spring Boot jar packaging
# Base: Eclipse Temurin (OpenJDK) 17
# ============================================================

FROM eclipse-temurin:17-jre-alpine AS runner

# Add non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy the built JAR from Maven target
# (Build context should be the src/backend directory)
COPY target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

USER appuser

# JVM optimization for containers
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
