# Beagen: A Beacon file generator for Deutsche Digitale Bibliothek
A Beacon file generator for [Deutsche Digitale Bibliothek](https://www.deutsche-digitale-bibliothek.de/). For more information about Beacon files see [this draft](http://gbv.github.io/beaconspec/beacon.html).

*Visit Beagen at DDBlabs:* https://labs.ddb.de/app/beagen
## See also
- GitHub: https://github.com/mbuechner/beagen
- Docker: https://hub.docker.com/r/mbuechner/beagen/
- Maven site: https://mbuechner.github.io/beagen/ 

## Screenshot
![Screenshot of Beagen](beagen.png "Beagen")

# Docker
[Beagen is at Docker Hub](https://hub.docker.com/r/mbuechner/beagen). To run the container execute:
```
docker run -d -p 80:80 -P \
    --env "beagen.baseurl=http://localhost/" \
    --env "beagen.cron=0 0 12 * * ?" \
    --env "beagen.database.dir=files/database/" \
    --env "beagen.log.dir=files/log/" \
    --env "beagen.ddbapikey=putinyourddbapikeyhere" \
mbuechner/beagen
```
*Note:* `beagen.database.dir` and `beagen.log.dir` should be direcories inside a Docker volume. If not all data will be lost on restart.

## Environment variables
| Variable            | Description                                                                                                                                                                    |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| beagen.baseurl      | Base url of Beagen application. Mainly used to build URLs in the Beacon file headers.                                                                                          |
| beagen.cron         | How often should the Job run and check for updates at DDB ([Quartz documentation](http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html)).       |
| beagen.database.dir | Directory for the database files ([Apache Derby](https://db.apache.org/derby/))                                                                                                |
| beagen.log.dir      | Directory for all locking files (See [configuration](https://github.com/mbuechner/beagen/blob/master/src/main/resources/logback.xml) of [Logback](https://logback.qos.ch/)).   |
| beagen.ddbapikey    | API key of Deutsche Digitale Bibliothek ([request](https://www.deutsche-digitale-bibliothek.de/user/apikey) and [documentation](https://api.deutsche-digitale-bibliothek.de/)) |

## Container build
If you like to build the Docker container by your own, please follow these steps. (Not necessary if you use the [pre-build container at Docker Hub](https://hub.docker.com/r/mbuechner/beagen/).)

1. Checkout GitHub repository: `git clone https://github.com/mbuechner/beagen`
2. Go into folder: `cd beagen`
3. Run `docker build -t beagen .`
4. Start container:
    ```
    docker run -d -p 80:80 -P \
        --env "beagen.baseurl=http://localhost/" \
        --env "beagen.cron=0 0 12 * * ?" \
        --env "beagen.database.dir=files/database/" \
        --env "beagen.log.dir=files/log/" \
        --env "beagen.ddbapikey=putinyourddbapikeyhere" \
    beagen
    ```
5. Open browser: http://localhost:80/

## Docker stack
If you're using [Docker Stack](https://docs.docker.com/engine/reference/commandline/stack/) to deploy application, this could be a possible configuration file in YAML.
```
version: '2'
services:
  beagen:
    image: mbuechner/beagen:latest
    volumes:
      - beagen:/home/beagen/files
    environment:
      - beagen.baseurl=https://example.com/beagen/
      - beagen.cron=0 0 12 * * ?
      - beagen.database.dir=/home/beagen/files/database/
      - beagen.log.dir=/home/beagen/files/log/
      - beagen.ddbapikey=putinyourddbapikeyhere
    ports:
      - "80"
    restart: always
volumes:
  beagen:
```	  

# Maven
It's a maven project comming with build-in [Jetty web server](http://www.eclipse.org/jetty/) ([Javalin](https://javalin.io/)). To build this project locally without using Docker, run inside the direcory with `pom.xml`:
```
mvn clean package
```
This will build a fat-jar with all dependencies. To run the webserver type:
```
java -jar target/beagen.jar
```
It'll run locally under: http://localhost:80/
