FROM maven:3-openjdk-15-slim AS MAVEN_CHAIN
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn package

FROM openjdk:15-alpine
RUN mkdir /home/beagen
COPY --from=MAVEN_CHAIN /tmp/target/beagen.jar /home/beagen/beagen.jar
WORKDIR /home/beagen/
CMD ["java", "-Xms512M", "-Xmx1G", "-jar", "beagen.jar"]

EXPOSE 8080
