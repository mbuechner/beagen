FROM maven:3-jdk-9-alpine AS MAVEN_CHAIN
COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn package

FROM openjdk:13-alpine3.9
COPY --from=MAVEN_CHAIN /tmp/target/ef2so.war $CATALINA_HOME/webapps/ROOT.war

HEALTHCHECK --interval=1m --timeout=3s CMD wget --quiet --tries=1 --spider http://localhost/list?type=organisation&sector=all || exit

EXPOSE 8080