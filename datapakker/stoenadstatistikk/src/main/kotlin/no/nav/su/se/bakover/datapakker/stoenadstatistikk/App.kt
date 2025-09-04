package no.nav.su.se.bakover.datapakker.stoenadstatistikk

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FormatOptions
import com.google.cloud.bigquery.Job
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
import java.time.YearMonth
import java.util.UUID

private val logger = LoggerFactory.getLogger("Stønadstatistikk")
private const val LOCATION = "europe-north1"

fun main() {
    logger.info("Starter opp Stønadstatistikk")
    val databaseUrl = System.getenv("DATABASE_JDBC_URL")
    val data = VaultPostgres(
        jdbcUrl = databaseUrl,
        vaultMountPath = System.getenv("VAULT_MOUNTPATH"),
        databaseName = System.getenv("DATABASE_NAME"),
    ).getDatasource(Postgres.Role.ReadOnly).let {
        logger.info("Startet database med url: $databaseUrl")
        hentData(it, YearMonth.now().minusMonths(1))
    }

    writeToBigQuery(data)
    logger.info("Slutter jobb Stønadstatistikk")
}

private fun createBigQueryClient(jsonKey: InputStream, project: String): BigQuery {
    val credentials = GoogleCredentials.fromStream(jsonKey)
    return BigQueryOptions.newBuilder()
        .setCredentials(credentials)
        .setLocation(LOCATION)
        .setProjectId(project)
        .build()
        .service
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

    val tableId = TableId.of(project, dataset, tableName)

    val writeConfig = WriteChannelConfiguration.newBuilder(tableId)
        .setFormatOptions(FormatOptions.csv())
        .build()

    val writer = bigQuery.writer(jobId, writeConfig)

    writer.use { channel ->
        Channels.newOutputStream(channel).use { os ->
            os.write(csvData.toByteArray())
        }
    }

    val job = writer.job
    job.waitFor()

    return job
}

val dataset = "statistikk"
fun writeToBigQuery(
    data: List<StønadstatistikkMånedDto>,
) {
    val jsonKey: InputStream = FileInputStream(File(System.getenv("BIGQUERY_CREDENTIALS")))
    val project: String = System.getenv("GCP_PROJECT")

    val bq = createBigQueryClient(jsonKey = jsonKey, project = project)

    val stoenadtable = "stoenadstatistikk"
    val stoenadCSV = data.toCSV()
    val jobStoenad = writeCsvToBigQueryTable(bigQuery = bq, project = project, tableName = stoenadtable, csvData = stoenadCSV)

    val månedstabell = "manedsbelop_statistikk"
    val alleMånedsBeløpCSV = data.mapNotNull {
        it.månedsbeløp?.toCSV(it.id)
    }.joinToString(separator = "")

    val maanedJob = writeCsvToBigQueryTable(bigQuery = bq, project = project, tableName = månedstabell, csvData = alleMånedsBeløpCSV)

    val fradragstabell = "fradrag_statistikk"
    val alleFradragsBeløpCSV = data.mapNotNull {
        it.månedsbeløp?.fradrag?.toCSV(it.månedsbeløp.manedsbelopId)
    }.joinToString(separator = "")

    val fradragjob = writeCsvToBigQueryTable(bigQuery = bq, project = project, tableName = fradragstabell, csvData = alleFradragsBeløpCSV)

    logger.info("Stønadstatistikkjob - stønad: ${jobStoenad.getStatistics<JobStatistics.LoadStatistics>()}")
    logger.info("Stønadstatistikkjob - måned: ${maanedJob.getStatistics<JobStatistics.LoadStatistics>()}")
    logger.info("Stønadstatistikkjob - fradrag: ${fradragjob.getStatistics<JobStatistics.LoadStatistics>()}")
}
