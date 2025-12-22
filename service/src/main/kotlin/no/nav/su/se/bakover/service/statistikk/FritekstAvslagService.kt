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
import no.nav.su.se.bakover.domain.statistikk.AvslagsvedtakUtenFritekst
import no.nav.su.se.bakover.domain.statistikk.FritekstAvslagRepo
import org.slf4j.LoggerFactory
import java.nio.channels.Channels
import java.util.UUID

interface FritekstAvslagService {
    fun hentOgSendAvslagFritekstTilBigquery()
}

private const val LOCATION = "europe-north1"

class FritekstAvslagServiceImpl(
    private val fritekstAvslagRepo: FritekstAvslagRepo,
) : FritekstAvslagService {
    val log = LoggerFactory.getLogger(FritekstAvslagServiceImpl::class.java)

    private fun hentAntallAvslagsvedtakUtenFritekst(): List<AvslagsvedtakUtenFritekst> = fritekstAvslagRepo.hentAntallAvslagsvedtakUtenFritekst()
    override fun hentOgSendAvslagFritekstTilBigquery() {
        deleteAllAndWriteToBigQuery(hentAntallAvslagsvedtakUtenFritekst())
    }

    fun deleteAllAndWriteToBigQuery(
        antallAvslagsvedtakUtenFritekst: List<AvslagsvedtakUtenFritekst>,
    ) {
        val table = "antallAvslagsvedtakUtenFritekst"
        val dataset = "avslagsvedtak"
        val project: String = System.getenv("GCP_TEAM_PROJECT_ID")

        val bq = createBigQueryClient(project)

        deleteAll(bq = bq, dataset = dataset, table = table)

        val jobId = JobId.newBuilder().setLocation(LOCATION).setJob(UUID.randomUUID().toString()).build()

        val configuration = WriteChannelConfiguration.newBuilder(
            TableId.of(project, dataset, table),
        ).setFormatOptions(FormatOptions.csv()).build()

        val job = bq.writer(jobId, configuration).let {
            it.use { channel ->
                Channels.newOutputStream(channel).use { os ->
                    os.write(antallAvslagsvedtakUtenFritekst.toCSV().toByteArray())
                }
            }
            it.job.waitFor()
        }

        log.info("job statistikk: ${job.getStatistics<JobStatistics.LoadStatistics>()}")
    }

    fun deleteAll(
        bq: BigQuery,
        dataset: String,
        table: String,
    ) {
        val query = QueryJobConfiguration.newBuilder(
            "DELETE FROM `$dataset.$table` WHERE true",
        ).setUseLegacySql(false).build()

        val result = bq.query(query)
        log.info("slettet antall linjer ${result.totalRows}")
    }

    private fun createBigQueryClient(project: String): BigQuery =
        BigQueryOptions.newBuilder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .setLocation(LOCATION)
            .setProjectId(project)
            .build()
            .service
}

fun List<AvslagsvedtakUtenFritekst>.toCSV(): String = this.joinToString(separator = "\n") {
    "${it.antall},${it.yearMonth}"
}
