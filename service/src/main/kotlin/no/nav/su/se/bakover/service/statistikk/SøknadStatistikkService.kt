package no.nav.su.se.bakover.service.statistikk

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FormatOptions
import com.google.cloud.bigquery.JobId
import com.google.cloud.bigquery.JobStatistics
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.WriteChannelConfiguration
import no.nav.su.se.bakover.domain.statistikk.DatapakkeSøknad
import no.nav.su.se.bakover.domain.statistikk.SøknadStatistikkRepo
import org.slf4j.LoggerFactory
import java.nio.channels.Channels
import java.util.UUID

interface SøknadStatistikkService {
    fun hentogSendSøknadStatistikkTilBigquery()
}

private const val LOCATION = "europe-north1"

class SøknadStatistikkServiceImpl(
    val søknadstatistikkrepo: SøknadStatistikkRepo,
) : SøknadStatistikkService {
    private val log = LoggerFactory.getLogger(SøknadStatistikkService::class.java)

    override fun hentogSendSøknadStatistikkTilBigquery() {
        val søknader = søknadstatistikkrepo.hentSøknaderAvType()
        deleteAndWriteToBigQuery(søknader)
    }

    val table = "papirVsDigital"
    val dataset = "soknad"
    private fun deleteAndWriteToBigQuery(søknader: List<DatapakkeSøknad>) {
        val project: String = System.getenv("GCP_TEAM_PROJECT_ID") ?: throw IllegalStateException("Påkrevd miljøvariabel GCP_TEAM_PROJECT_ID er ikke satt")

        val bqClient = createBigQueryClient(project)

        deleteAll(bqClient)

        val jobId = JobId.newBuilder().setLocation(LOCATION).setJob(UUID.randomUUID().toString()).build()

        val writeConfig = WriteChannelConfiguration.newBuilder(TableId.of(project, dataset, table))
            .setFormatOptions(FormatOptions.csv()).build()

        val writer = try {
            bqClient.writer(jobId, writeConfig)
        } catch (e: Exception) {
            throw RuntimeException("BigQuery writer creation failed: ${e.message}", e)
        }
        try {
            writer.use { channel ->
                Channels.newOutputStream(channel).use { os ->
                    os.write(søknader.toCSV().toByteArray())
                }
            }
        } catch (e: Exception) {
            log.error("Failed to write CSV data to BigQuery stream: ${e.message}", e)
            throw RuntimeException("Error during CSV write to BigQuery", e)
        }
        val job = writer.job
        job.waitFor()

        log.info("job statistikk: ${job.getStatistics<JobStatistics.LoadStatistics>()}")
    }

    private fun createBigQueryClient(project: String): BigQuery =
        BigQueryOptions.newBuilder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .setLocation(LOCATION)
            .setProjectId(project)
            .build()
            .service

    private fun deleteAll(
        bq: BigQuery,
    ) {
        val query = QueryJobConfiguration.newBuilder(
            "DELETE FROM `$dataset.$table` WHERE true",
        ).setUseLegacySql(false).build()

        val result = bq.query(query)
        log.info("slettet antall linjer ${result.totalRows}")
    }
}

private fun List<DatapakkeSøknad>.toCSV(): String {
    return this.joinToString(separator = "\n") {
        "${it.id},${it.opprettet},${it.type},${it.mottaksdato}"
    }
}
