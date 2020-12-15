FROM maven:3-openjdk-16-slim AS MAVEN_CHAIN
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn package

FROM openjdk:16-alpine
RUN mkdir /home/beagen
COPY --from=MAVEN_CHAIN /tmp/target/beagen.jar /home/beagen/beagen.jar
WORKDIR /home/beagen/
CMD ["java", "-jar", "beagen.jar"]

HEALTHCHECK --interval=1m --timeout=3s CMD wget --quiet --tries=1 --spider http://localhost/list/latest?type=person&sector=all || exit

EXPOSE 80
