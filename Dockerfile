FROM openjdk:17-jdk-alpine

RUN addgroup -S dome && adduser -S bscheduler -G dome

USER bscheduler:dome

COPY ./target/billing-scheduler*.jar /usr/app/billing-scheduler.jar

EXPOSE 8080/tcp 

ENTRYPOINT ["java","-jar","/billing-scheduler.jar"]