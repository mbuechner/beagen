![Java CI with Maven](https://github.com/mbuechner/beagen/workflows/Java%20CI%20with%20Maven/badge.svg) ![Docker](https://github.com/mbuechner/beagen/workflows/Docker/badge.svg)
# Beagen: A Beacon file generator for Deutsche Digitale Bibliothek
A Beacon file generator for [Deutsche Digitale Bibliothek](https://www.deutsche-digitale-bibliothek.de/). For more information about Beacon files see [this draft](http://gbv.github.io/beaconspec/beacon.html).

*Visit Beagen at DDBlabs:* https://labs.ddb.de/app/beagen
## See also
- GitHub: https://github.com/mbuechner/beagen
- Maven site: https://mbuechner.github.io/beagen/ 

## Screenshot
![Screenshot of Beagen](beagen.png "Beagen")

# Docker
[Beagen is available as Docker container at GitHub](https://github.com/mbuechner/beagen/pkgs/container/beagen%2Fbeagen). To run the container execute:
```
docker run -d -p 8080:8080 -P \
    --env "beagen.baseurl=http://localhost/" \
    --env "beagen.cron=0 0 12 * * ?" \
    --env "beagen.database.dir=files/database/" \
ghcr.io/mbuechner/beagen/beagen:latest
```
*Note:* `beagen.database.dir` should be a directory inside a Docker volume. If not all data will be lost on restart.

## Environment variables
| Variable            | Description                                                                                                                                                                       |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| beagen.baseurl      | Base url of Beagen application. Mainly used to build URLs in the Beacon file headers.                                                                                             |
| beagen.cron         | How often should the Job run and check for updates at DDB ([Quartz documentation](http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/tutorial-lesson-06.html)). |
| beagen.database.dir | Directory for the database files ([Apache Derby](https://db.apache.org/derby/))                                                                                                   |

## Container build
If you like to build the Docker container by yourself, please follow these steps. (Not necessary if you use the [pre-build container at GitHub](https://github.com/mbuechner/beagen/pkgs/container/beagen%2Fbeagen).)

1. Checkout GitHub repository: `git clone https://github.com/mbuechner/beagen`
2. Go into folder: `cd beagen`
3. Run `docker build -t beagen .`
4. Start container:
    ```
    docker run -d -p 8080:8080 -P \
        --env "beagen.baseurl=http://localhost/" \
        --env "beagen.cron=0 0 12 * * ?" \
        --env "beagen.database.dir=files/database/" \
    beagen
    ```
5. Open browser: http://localhost:8080/

## Docker stack
If you're using [Docker Stack](https://docs.docker.com/engine/reference/commandline/stack/) to deploy application, this could be a possible configuration file in YAML.
```
version: '2'
services:
  beagen:
    image: ghcr.io/mbuechner/beagen/beagen:latest
    volumes:
      - beagen:/home/beagen/files
    environment:
      - "beagen.baseurl=https://example.com/beagen/"
      - "beagen.cron=0 0 12 * * ?"
      - "beagen.database.dir=/home/beagen/files/database/"
    ports:
      - "8080"
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
It'll run locally under: http://localhost:8080/
