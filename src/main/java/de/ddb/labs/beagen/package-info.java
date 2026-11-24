/**
 * Beagen publishes BEACON authority files from Deutsche Digitale Bibliothek data.
 *
 * <p>The HTTP server provides browsing and JSON metadata at {@code /list} and
 * {@code /list/latest}. Clients can download a specific file by ID or follow a
 * stable link to the latest file for a type and sector. All routes use the
 * configured URL prefix when deployed behind a reverse proxy.
 *
 * <p>The {@linkplain de.ddb.labs.beagen.backend.jobs.BeaconJob scheduled importer}
 * retrieves DDB data and stores compressed snapshots in H2. The server uses
 * those snapshots to provide historic downloads and their metadata.
 */
package de.ddb.labs.beagen;
