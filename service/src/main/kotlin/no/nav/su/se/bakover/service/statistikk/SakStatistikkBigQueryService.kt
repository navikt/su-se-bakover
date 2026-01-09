package no.nav.su.se.bakover.service.statistikk

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
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import org.slf4j.LoggerFactory
import java.nio.channels.Channels
import java.time.LocalDate
import java.util.UUID

private const val LOCATION = "europe-north1"

interface SakStatistikkBigQueryService {
    fun lastTilBigQuery(fom: LocalDate)
}

/*
Skal inn i cron jobs og kjøre en gang hver natt 01:00
 */
// https://docs.nais.io/workloads/application/reference/application-spec/#gcpbigquerydatasets
class SakStatistikkBigQueryServiceImpl(
    private val repo: SakStatistikkRepo,
) : SakStatistikkBigQueryService {
    private val logger = LoggerFactory.getLogger(SakStatistikkBigQueryService::class.java)

    override fun lastTilBigQuery(fom: LocalDate) {
        val data = hentSaksstatistikk(fom)

        logger.info("Hentet ${data.size} rader fra databasen")
        writeToBigQuery(data)
        logger.info("Slutter jobb Saksstatistikk")
    }

    private fun hentSaksstatistikk(fom: LocalDate): List<SakStatistikk> =
        repo.hentSakStatistikk(fom, tom = LocalDate.now().plusDays(1))

    private fun writeToBigQuery(data: List<SakStatistikk>) {
        /*
            https://docs.nais.io/persistence/bigquery/how-to/connect/?h=bigquery
            defaulty inject basert på yaml filens referanses
         */
        val project: String = System.getenv("GCP_TEAM_PROJECT_ID")

        val bq = createBigQueryClient(project)

        val table = "saksstatistikk"
        val csv = data.toCsv()
        logger.info("Skriver ${csv.length} bytes til BigQuery-tabell: $table")

        val job = writeCsvToBigQueryTable(bq, project, table, csv)

        logger.info("Saksstatistikkjobb: ${job.getStatistics<JobStatistics.LoadStatistics>()}")
    }
    private fun createBigQueryClient(project: String): BigQuery =
        BigQueryOptions.newBuilder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .setLocation(LOCATION)
            .setProjectId(project)
            .build()
            .service

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
}

private fun List<SakStatistikk>.toCsv(): String = buildString {
    for (sakStatistikk in this@toCsv) {
        appendLine(
            listOf(
                sakStatistikk.getSekvensId().toString(),
                sakStatistikk.funksjonellTid.toString(),
                sakStatistikk.tekniskTid.toString(),
                sakStatistikk.sakId.toString(),
                sakStatistikk.saksnummer.toString(),
                sakStatistikk.behandlingId.toString(),
                sakStatistikk.relatertBehandlingId?.toString().orEmpty(),
                sakStatistikk.aktorId.toString(),
                sakStatistikk.sakYtelse,
                sakStatistikk.sakUtland,
                sakStatistikk.behandlingType,
                sakStatistikk.behandlingMetode.name,
                sakStatistikk.mottattTid.toString(),
                sakStatistikk.registrertTid.toString(),
                sakStatistikk.ferdigbehandletTid?.toString().orEmpty(),
                sakStatistikk.utbetaltTid?.toString().orEmpty(),
                sakStatistikk.behandlingStatus,
                sakStatistikk.behandlingResultat.orEmpty(),
                sakStatistikk.resultatBegrunnelse.orEmpty(),
                sakStatistikk.behandlingAarsak.orEmpty(),
                sakStatistikk.opprettetAv.orEmpty(),
                sakStatistikk.saksbehandler.orEmpty(),
                sakStatistikk.ansvarligBeslutter.orEmpty(),
                sakStatistikk.ansvarligEnhet,
                sakStatistikk.fagsystemNavn,
                sakStatistikk.funksjonellPeriodeFom?.toString().orEmpty(),
                sakStatistikk.funksjonellPeriodeTom?.toString().orEmpty(),
                sakStatistikk.tilbakekrevBeløp?.toString().orEmpty(),
            ).joinToString(",") { escapeCsv(it) },
        )
    }
}

private fun escapeCsv(field: String): String {
    val needsQuotes = field.contains(",") || field.contains("\"") || field.contains("\n")
    val escaped = field.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}
