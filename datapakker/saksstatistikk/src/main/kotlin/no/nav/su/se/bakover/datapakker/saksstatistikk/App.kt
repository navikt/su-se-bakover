package no.nav.su.se.bakover.datapakker.saksstatistikk

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryException
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FormatOptions
import com.google.cloud.bigquery.Job
import com.google.cloud.bigquery.JobId
import com.google.cloud.bigquery.JobStatistics
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.WriteChannelConfiguration
import no.nav.su.se.bakover.database.Postgres
import no.nav.su.se.bakover.database.VaultPostgres
import no.nav.su.se.bakover.datapakker.saksstatistikk.SakStatistikkRepo.hentData
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.channels.Channels
import java.time.LocalDate
import java.util.UUID

private val logger = LoggerFactory.getLogger("Saksstatistikk")
private const val LOCATION = "europe-north1"

fun main() {
    logger.info("Starter opp Saksstatistikk")
    val databaseUrl = System.getenv("DATABASE_JDBC_URL")
    val data = VaultPostgres(
        jdbcUrl = databaseUrl,
        vaultMountPath = System.getenv("VAULT_MOUNTPATH"),
        databaseName = System.getenv("DATABASE_NAME"),
        maximumPoolSize = 1,
    ).getDatasource(Postgres.Role.ReadOnly).let {
        logger.info("Startet database med url: $databaseUrl")

        // Default:
        // hentData(it, LocalDate.now())

        // Endres manuelt ved uthenting tilbake i tid
        hentData(dataSource = it, fom = LocalDate.of(2025, 10, 6), tom = LocalDate.now().plusDays(1))
    }

    logger.info("Hentet ${data.size} rader fra databasen")
    writeToBigQuery(data)
    logger.info("Slutter jobb Saksstatistikk")
}

private fun createBigQueryClient(jsonKey: InputStream, project: String): BigQuery {
    val credentials = GoogleCredentials.fromStream(jsonKey)
    return BigQueryOptions
        .newBuilder()
        .setCredentials(credentials)
        .setLocation(LOCATION)
        .setProjectId(project)
        .build().service
}

private fun writeCsvToBigQueryTable(
    bigQuery: BigQuery,
    project: String,
    tableName: String,
    csvData: String,
): Job {
    val jobId = JobId.newBuilder()
        .setLocation(LOCATION)
        .setJob(UUID.randomUUID().toString())
        .build()

    val dataset = "saksstatistikk"
    val tableId = TableId.of(project, dataset, tableName)

    logger.info("Writing csv to bigquery. id: $jobId, project: $project, table: $tableId")

    val writeConfig = WriteChannelConfiguration.newBuilder(tableId)
        .setFormatOptions(FormatOptions.csv())
        .build()

    val writer = try {
        bigQuery.writer(jobId, writeConfig)
    } catch (e: BigQueryException) {
        throw RuntimeException("BigQuery writer creation failed: ${e.message}", e)
    }

    try {
        writer.use { channel ->
            Channels.newOutputStream(channel).use { os ->
                os.write(csvData.toByteArray())
            }
        }
    } catch (e: Exception) {
        logger.error("Failed to write CSV data to BigQuery stream: ${e.message}", e)
        throw RuntimeException("Error during CSV write to BigQuery", e)
    }

    val job = writer.job
    job.waitFor()

    return job
}

fun writeToBigQuery(data: List<SakStatistikk>) {
    val jsonKey: InputStream = FileInputStream(File(System.getenv("BIGQUERY_CREDENTIALS")))
    val project: String = System.getenv("GCP_PROJECT")

    val bq = createBigQueryClient(jsonKey, project)

    val table = "saksstatistikk"
    val csv = data.toCsv()
    logger.info("Skriver ${csv.length} bytes til BigQuery-tabell: $table")

    val job = writeCsvToBigQueryTable(bq, project, table, csv)

    logger.info("Saksstatistikkjobb: ${job.getStatistics<JobStatistics.LoadStatistics>()}")
}
