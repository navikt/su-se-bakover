package no.nav.su.se.bakover.service.statistikk

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryException
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FormatOptions
import com.google.cloud.bigquery.Job
import com.google.cloud.bigquery.JobId
import com.google.cloud.bigquery.JobInfo
import com.google.cloud.bigquery.JobStatistics
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.QueryParameterValue
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.WriteChannelConfiguration
import no.nav.su.se.bakover.common.domain.statistikk.SakStatistikk
import org.slf4j.LoggerFactory
import java.nio.channels.Channels
import java.util.UUID

private const val LOCATION = "europe-north1"
private const val DATASET = "saksstatistikk"
private const val TABLE = "saksstatistikk"

class SakStatistikkBigQueryGatewayImpl : SakStatistikkBigQueryGateway {
    private val logger = LoggerFactory.getLogger(SakStatistikkBigQueryGateway::class.java)
    private val project: String by lazy {
        System.getenv("GCP_TEAM_PROJECT_ID")
            ?: throw IllegalStateException("Påkrevd miljøvariabel GCP_TEAM_PROJECT_ID er ikke satt")
    }
    private val bigQuery: BigQuery by lazy {
        BigQueryOptions.newBuilder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .setLocation(LOCATION)
            .setProjectId(project)
            .build()
            .service
    }
    private val tableId: TableId by lazy { TableId.of(project, DATASET, TABLE) }
    private val fullTableName: String by lazy { "`$project.$DATASET.$TABLE`" }

    override fun hentAntallRaderPerSekvensId(sekvensIder: Set<Long>): Map<Long, Long> {
        val query = QueryJobConfiguration.newBuilder(
            """
            SELECT id, COUNT(*) AS antall
            FROM $fullTableName
            WHERE id IN UNNEST(@sekvensIder)
            GROUP BY id
            """.trimIndent(),
        )
            .setUseLegacySql(false)
            .addNamedParameter("sekvensIder", sekvensIder.toBigQueryArray())
            .build()

        return bigQuery.query(query).iterateAll().associate {
            it["id"].longValue to it["antall"].longValue
        }
    }

    override fun deleteExactlyOrVerifyMissing(sekvensIder: Set<Long>) {
        val query = QueryJobConfiguration.newBuilder(
            """
            DECLARE antall_valgte_rader INT64;
            DECLARE antall_valgte_ider INT64;

            BEGIN
              BEGIN TRANSACTION;

              SET (antall_valgte_rader, antall_valgte_ider) = (
                SELECT AS STRUCT
                  COUNT(*),
                  COUNT(DISTINCT id)
                FROM $fullTableName
                WHERE id IN UNNEST(@sekvensIder)
              );

              ASSERT (
                antall_valgte_rader = 0
                OR (
                  antall_valgte_rader = @forventetAntall
                  AND antall_valgte_ider = @forventetAntall
                )
              ) AS 'BigQuery har en delvis eller duplisert mengde av de forespurte sekvens-ID-ene';

              IF antall_valgte_rader = @forventetAntall THEN
                DELETE FROM $fullTableName
                WHERE id IN UNNEST(@sekvensIder);

                ASSERT @@row_count = @forventetAntall
                AS 'BigQuery slettet et uventet antall rader';
              END IF;

              COMMIT TRANSACTION;
            EXCEPTION WHEN ERROR THEN
              ROLLBACK TRANSACTION;
              RAISE;
            END;
            """.trimIndent(),
        )
            .setUseLegacySql(false)
            .addNamedParameter("sekvensIder", sekvensIder.toBigQueryArray())
            .addNamedParameter("forventetAntall", QueryParameterValue.int64(sekvensIder.size))
            .build()

        bigQuery.query(query)
        logger.info("BigQuery-tabellen $TABLE er klar for opplasting av ${sekvensIder.size} validerte rader")
    }

    override fun writeToBigQuery(data: List<SakStatistikk>) {
        val csv = data.toCsv()
        logger.info("Bytes til GCP ${csv.length}, til BigQuery-tabell: $TABLE")
        val job = writeCsvToBigQueryTable(csv, data.size)
        logger.info("Saksstatistikkjobb: ${job.getStatistics<JobStatistics.LoadStatistics>()}")
    }

    override fun verifyExactlyOnce(sekvensIder: Set<Long>) {
        val query = QueryJobConfiguration.newBuilder(
            """
            DECLARE antall_rader INT64;
            DECLARE antall_ider INT64;

            SET (antall_rader, antall_ider) = (
              SELECT AS STRUCT
                COUNT(*),
                COUNT(DISTINCT id)
              FROM $fullTableName
              WHERE id IN UNNEST(@sekvensIder)
            );

            ASSERT antall_rader = @forventetAntall
            AS 'Ikke alle sekvens-ID-ene finnes nøyaktig én gang etter opplasting';

            ASSERT antall_ider = @forventetAntall
            AS 'En eller flere sekvens-ID-er er duplisert etter opplasting';
            """.trimIndent(),
        )
            .setUseLegacySql(false)
            .addNamedParameter("sekvensIder", sekvensIder.toBigQueryArray())
            .addNamedParameter("forventetAntall", QueryParameterValue.int64(sekvensIder.size))
            .build()

        bigQuery.query(query)
    }

    private fun writeCsvToBigQueryTable(csvData: String, forventetAntallRader: Int): Job {
        val jobId = JobId.newBuilder()
            .setLocation(LOCATION)
            .setJob(UUID.randomUUID().toString())
            .build()

        logger.info("Writing csv to bigquery. id: $jobId, project: $project, table: $tableId")

        val writeConfig = WriteChannelConfiguration.newBuilder(tableId)
            .setFormatOptions(FormatOptions.csv())
            .setWriteDisposition(JobInfo.WriteDisposition.WRITE_APPEND)
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

        val completedJob = try {
            writer.job.waitFor()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException("Ventingen på BigQuery-jobben ble avbrutt", e)
        } ?: throw IllegalStateException("BigQuery-jobben forsvant før den var ferdig")

        completedJob.status.error?.let {
            throw IllegalStateException("BigQuery-jobben feilet: ${it.reason}: ${it.message}")
        }
        val loadStatistics = completedJob.getStatistics<JobStatistics.LoadStatistics>()
        check(loadStatistics.outputRows == forventetAntallRader.toLong()) {
            "BigQuery lastet ${loadStatistics.outputRows} rader, men forventet $forventetAntallRader"
        }
        return completedJob
    }
}

private fun Set<Long>.toBigQueryArray(): QueryParameterValue =
    QueryParameterValue.array(toTypedArray(), Long::class.javaObjectType)

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
