# Beagen

[Deutsche Digitale Bibliothek](https://www.deutsche-digitale-bibliothek.de/) → Quartz import → gzip BEACON files in H2 → Javalin downloads and HTML/JSON history. [Live](https://labs.ddb.de/app/beagen)

## Run

```sh
docker run --rm -p 8080:8080 -v beagen:/home/beagen/files \
  ghcr.io/mbuechner/beagen:latest
```

Open `http://localhost:8080/`. The volume persists H2 across container replacements.

Use Java 25 and Maven 3.9+ for local development.

```sh
mvn clean verify && java -jar target/beagen.jar
```

See [defaults](src/main/resources/beagen.cfg) for `beagen.port`, `beagen.cron` and `beagen.database`. Behind a reverse proxy, set `beagen.baseurl` to the public URL and `beagen.pathprefix` to the forwarded prefix. Set all five via environment variables.

## From PR to OpenShift

OpenShift tracks the same `latest` image shown above. The daily build batches merged code and dependency changes with any base-image updates.

1. PRs run `mvn verify`. Dependabot checks daily and bundles Maven minor and patch updates into one routine PR. Pushes and bot merges on `master` refresh the Maven dependency graph.
2. At **08:17 Europe/Berlin**, CI pulls fresh Java 25 base images and builds `master`. The [container check](.github/actions/check-container/action.yml) probes HTTP as non-root and scans the image and its Maven SBOM before Cosign signs it. The workflow supplies the port, startup settings and optional SBOM path.
3. GHCR moves `latest` when the signed digest changes. OpenShift rolls it out on the next poll.

Push a Git tag like `v1.9.1` for an immutable release image. For an urgent `latest` build, use **Actions → Docker → Run workflow** on `master`.

### Dependabot settings

Optional auto-merge uses `DEPENDABOT_AUTO_MERGE=true`, squash merges and the required **Maven verify** check on `master`. Remove **Container verification** from existing required checks.

For Critical-only security PRs, keep Dependabot alerts on, turn broad security updates off and [set an auto-triage rule](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/manage-your-dependency-security/auto-triage-dependabot-alerts) to open PRs for Critical alerts with a fix. Filter email using `X-GitHub-Severity: critical`.
