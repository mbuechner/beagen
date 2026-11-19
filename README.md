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

## From release to OpenShift

1. PRs run `mvn verify`. Dependabot groups Maven minor and patch updates. Publishing a stable GitHub Release queues its versioned container build on `master`. Tag a commit that contains the release trigger.
2. At **08:17 Europe/Berlin**, CI checks the release and its Linux/amd64 base image digests, including `amazoncorretto:25`. It rebuilds when the inputs change and scans the image and its Maven SBOM each day. The [container check](.github/actions/check-container/action.yml) also probes HTTP as non-root.
3. GHCR updates the version tag after verification and signing. For the maintained release, the same run also updates `latest` and `tagged`. Point OpenShift's periodic import at a version tag to keep that application version while picking up Java and OS updates.

CI maintains the latest stable GitHub Release by default. Set the repository variable `CONTAINER_RELEASE_TAG` to keep refreshing a specific published release after newer releases appear. For an urgent update check, run **Actions → Docker → Run workflow** on `master`. Use `release_tag` to select a specific published release.

### Dependabot settings

Optional auto-merge uses `DEPENDABOT_AUTO_MERGE=true`, squash merges and the required **Maven verify** check on `master`. Remove **Container verification** from existing required checks.

For Critical-only security PRs, keep Dependabot alerts on, turn broad security updates off and [set an auto-triage rule](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/manage-your-dependency-security/auto-triage-dependabot-alerts) to open PRs for Critical alerts with a fix. Filter email using `X-GitHub-Severity: critical`.
