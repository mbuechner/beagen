FROM maven:3-eclipse-temurin-21-alpine AS maven_chain
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn clean package

FROM eclipse-temurin:21-jre-alpine
RUN mkdir /home/beagen
COPY --from=maven_chain /tmp/target/beagen.jar /home/beagen/beagen.jar
WORKDIR /home/beagen/
CMD ["java", "-Xms512M", "-Xmx1G", "-jar", "beagen.jar"]

EXPOSE 8080
