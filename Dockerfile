FROM maven:3-amazoncorretto-25 AS maven_chain
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn -B -ntp clean verify

FROM amazoncorretto:25
RUN mkdir -p /home/beagen/files && chmod g+w /home/beagen/files
COPY --from=maven_chain /tmp/target/beagen.jar /tmp/target/beagen-sbom.json /home/beagen/
WORKDIR /home/beagen/
CMD ["java", "-Xms512M", "-Xmx1G", "-jar", "beagen.jar"]

EXPOSE 8080
