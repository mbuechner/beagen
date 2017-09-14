FROM maven:alpine # image based on smaller Alpine Linux
COPY . /usr/src/app
RUN mvn clean package exec:java
EXPOSE 80 # expose container ports (80 for the application)
 
# ADD WAR (change it accordingly to the name of your .war file)
# ADD example-0.0.1-SNAPSHOT.war /usr/local/glassfish4/glassfish/domains/domain1/autodeploy/
# SETUP/START GF
# ADD init_gf.sh /home/
# RUN chmod +x /home/init_gf.sh
# CMD /home/init_gf.sh
