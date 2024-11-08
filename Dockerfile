FROM openjdk:17-jdk-alpine

RUN addgroup -S dome && adduser -S bengine -G dome

USER bengine:dome

COPY ./target/billing-engine*.jar /usr/app/billing-engine.jar

EXPOSE 8080/tcp 

ENTRYPOINT ["java","-jar","/billing-engine.jar"]