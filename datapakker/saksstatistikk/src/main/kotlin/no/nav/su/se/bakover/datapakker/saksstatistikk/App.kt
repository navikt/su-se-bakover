package no.nav.su.se.bakover.datapakker.saksstatistikk

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FormatOptions
import com.google.cloud.bigquery.JobId
import com.google.cloud.bigquery.JobStatistics
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.WriteChannelConfiguration
import no.nav.su.se.bakover.database.Postgres
import no.nav.su.se.bakover.database.VaultPostgres
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.channels.Channels
import java.util.UUID

private val logger = LoggerFactory.getLogger("Saksstatistikk")
private const val LOCATION = "europe-north1"

fun main() {
    logger.info("Starter opp Saksstatistikk")
    val databaseUrl = System.getenv("DATABASE_JDBC_URL")
    val datasource = VaultPostgres(
        jdbcUrl = databaseUrl,
        vaultMountPath = System.getenv("VAULT_MOUNTPATH"),
        databaseName = System.getenv("DATABASE_NAME"),
    ).getDatasource(Postgres.Role.ReadOnly).let {
        logger.info("Startet database med url: $databaseUrl")
        it
    }
    /*
        Her kommer koden for å hente data fra databasen, transformere den og laste den opp til BigQuery
        mangler
        writeToBigQuery(datasource.getStatistikk())
     */
    writeToBigQuery()
    logger.info("Slutter jobb Saksstatistikk")
}

fun writeToBigQuery(
    data: String = "",
) {
    val jsonKey: InputStream = FileInputStream(File(System.getenv("BIGQUERY_CREDENTIALS")))
    val project: String = System.getenv("GCP_PROJECT")

    val dataset = "statistikk"
    val table = "saksstatistikk"

    val credentials = GoogleCredentials.fromStream(jsonKey)
    val bq = BigQueryOptions
        .newBuilder()
        .setCredentials(credentials)
        .setLocation(LOCATION)
        .setProjectId(project)
        .build().service

    val jobId = JobId.newBuilder().setLocation(LOCATION).setJob(UUID.randomUUID().toString()).build()
    val configuration = WriteChannelConfiguration.newBuilder(
        TableId.of(project, dataset, table),
    ).setFormatOptions(FormatOptions.csv()).build()

    val csvData = listOf(
        "hello",
        "world",
        "this is a test",
        "another row",
    ).joinToString("\n")

    val job = bq.writer(jobId, configuration).let {
        it.use { channel ->
            Channels.newOutputStream(channel).use { os ->
                os.write(csvData.toByteArray()) // TODO: her må vi ha dataen fra vår tabell + må lage dump av vår sql inn i bigquery
            }
        }
        it.job.waitFor()
    }

    logger.info("Saksstatistikkjobb: ${job.getStatistics<JobStatistics.LoadStatistics>()}")
}
