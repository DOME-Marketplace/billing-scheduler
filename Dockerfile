
# Use Java base image JDK 17
FROM eclipse-temurin:17-jdk-alpine 

# Install curl
RUN apk update && apk add --no-cache curl

# Set the workdir in the container
WORKDIR /usr/app

# Copy JAR in the working directory
COPY target/billing-scheduler.jar billing-scheduler.jar

# Espose port 8080
EXPOSE 8080

# Comand to run the Spring Boot application
ENTRYPOINT ["java","-jar","billing-scheduler.jar"]