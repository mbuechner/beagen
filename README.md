# Beagen

Deutsche Digitale Bibliothek → Quartz → gzip BEACON in H2 → downloads + HTML/JSON history. [Live](https://labs.ddb.de/app/beagen)

## Run

```sh
docker run --rm -p 8080:8080 -v beagen:/home/beagen/files ghcr.io/mbuechner/beagen:latest
```

Open `http://localhost:8080/`. H2 lives in `database/` on the volume. Give the container write access.

Build with Java 25 and `./mvnw clean verify` (`.\mvnw.cmd clean verify` on Windows). Start with `java -jar target/beagen.jar`.

Override runtime settings via environment variables. Behind a proxy, set `beagen.baseurl` to the public URL and `beagen.pathprefix` to the forwarded prefix. [Defaults](src/main/resources/beagen.cfg)

## Trigger an import

Set `beagen.trigger.token` on the server and restart. Set `BEAGEN_TRIGGER_TOKEN` to the same secret in the caller's shell. Include `beagen.pathprefix` in the URL if configured.

```sh
curl --fail-with-body -X POST -H "Authorization: Bearer $BEAGEN_TRIGGER_TOKEN" http://localhost:8080/trigger
```

`202` full import queued · `401` missing or invalid token · `409` manual import already queued or running. An empty server token disables the route.

## Deploy

Stable GitHub Release → signed GHCR image. Pin a version tag in OpenShift. `CONTAINER_RELEASE_TAG` selects the release CI refreshes. [Docker workflow](.github/workflows/docker-publish.yml)
