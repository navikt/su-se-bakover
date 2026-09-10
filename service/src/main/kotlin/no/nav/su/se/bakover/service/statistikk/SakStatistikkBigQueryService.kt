package no.nav.su.se.bakover.service.statistikk

import arrow.core.Either
import arrow.core.left
import arrow.core.right
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
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import org.slf4j.LoggerFactory
import java.nio.channels.Channels
import java.time.LocalDate
import java.util.UUID

private const val LOCATION = "europe-north1"
private const val DATASET = "saksstatistikk"
private const val TABLE = "saksstatistikk"
private const val MAKS_ANTALL_SEKVENS_IDER = 500

interface SakStatistikkBigQueryService {
    fun lastTilBigQuery(
        fraOgMed: LocalDate,
        tilOgMed: LocalDate = fraOgMed,
    )

    fun erstattSakStatistikk(
        sekvensIder: List<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk, ErstattetSakStatistikk>

    fun forhåndsvisErstattSakStatistikk(
        sekvensIder: List<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk, ForhåndsvisErstattSakStatistikk>
}

sealed interface KunneIkkeErstatteSakStatistikk {
    data object IngenSekvensIder : KunneIkkeErstatteSakStatistikk
    data class ForMangeSekvensIder(val maksimaltAntall: Int) : KunneIkkeErstatteSakStatistikk
    data class UgyldigeSekvensIder(val sekvensIder: Set<Long>) : KunneIkkeErstatteSakStatistikk
    data class DuplikateSekvensIder(val sekvensIder: Set<Long>) : KunneIkkeErstatteSakStatistikk
    data class ManglerISakStatistikk(val sekvensIder: Set<Long>) : KunneIkkeErstatteSakStatistikk
    data class IkkeUnikeISakStatistikk(val sekvensIder: Set<Long>) : KunneIkkeErstatteSakStatistikk
    data class UgyldigTilstandIBigQuery(
        val antallForespurte: Int,
        val antallRaderIBigQuery: Long,
    ) : KunneIkkeErstatteSakStatistikk
}

data class ErstattetSakStatistikk(val antall: Int)

data class ForhåndsvisErstattSakStatistikk(
    val kanErstattes: Boolean,
    val antallForespurte: Int,
    val antallRaderISakStatistikk: Int,
    val antallRaderIBigQuery: Long,
    val manglendeISakStatistikk: List<Long>,
    val ikkeUnikeISakStatistikk: List<Long>,
)

private data class PostgresErstatningskontroll(
    val data: List<SakStatistikk>,
    val manglendeSekvensIder: List<Long>,
    val ikkeUnikeSekvensIder: List<Long>,
) {
    val kanErstattes = manglendeSekvensIder.isEmpty() && ikkeUnikeSekvensIder.isEmpty()

    fun validerForErstatning(): Either<KunneIkkeErstatteSakStatistikk, List<SakStatistikk>> {
        if (manglendeSekvensIder.isNotEmpty()) {
            return KunneIkkeErstatteSakStatistikk.ManglerISakStatistikk(manglendeSekvensIder.toSet()).left()
        }
        if (ikkeUnikeSekvensIder.isNotEmpty()) {
            return KunneIkkeErstatteSakStatistikk.IkkeUnikeISakStatistikk(ikkeUnikeSekvensIder.toSet()).left()
        }
        return data.right()
    }
}

private data class BigQueryErstatningskontroll(
    val antallForespurte: Int,
    val raderPerSekvensId: Map<Long, Long>,
) {
    val antallRader = raderPerSekvensId.values.sum()
    val kanErstattes =
        raderPerSekvensId.isEmpty() ||
            (raderPerSekvensId.size == antallForespurte && raderPerSekvensId.values.all { it == 1L })

    fun validerForErstatning(): Either<KunneIkkeErstatteSakStatistikk, Unit> =
        if (kanErstattes) {
            Unit.right()
        } else {
            KunneIkkeErstatteSakStatistikk.UgyldigTilstandIBigQuery(
                antallForespurte = antallForespurte,
                antallRaderIBigQuery = antallRader,
            ).left()
        }
}

/*
Skal inn i cron jobs og kjøre en gang hver natt 01:00
 */
// https://docs.nais.io/workloads/application/reference/application-spec/#gcpbigquerydatasets
class SakStatistikkBigQueryServiceImpl(
    private val repo: SakStatistikkRepo,
    private val bigQueryGateway: SakStatistikkBigQueryGateway,
) : SakStatistikkBigQueryService {
    private val logger = LoggerFactory.getLogger(SakStatistikkBigQueryService::class.java)

    override fun lastTilBigQuery(fraOgMed: LocalDate, tilOgMed: LocalDate) {
        val data = repo.hentSakStatistikk(fraOgMed, tilOgMed)
        logger.info("Hentet ${data.size} rader fra databasen")
        if (data.isNotEmpty()) {
            bigQueryGateway.writeToBigQuery(data)
        }
        logger.info("Slutter jobb Saksstatistikk")
    }

    override fun erstattSakStatistikk(
        sekvensIder: List<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk, ErstattetSakStatistikk> {
        val validerteSekvensIder = when (val validering = validerSekvensIder(sekvensIder)) {
            is Either.Left -> return validering.value.left()
            is Either.Right -> validering.value
        }
        val postgresKontroll = kontrollerPostgres(sekvensIder, validerteSekvensIder)
        val data = postgresKontroll.validerForErstatning().fold(
            ifLeft = { return it.left() },
            ifRight = { it },
        )
        val bigQueryKontroll = kontrollerBigQuery(sekvensIder, validerteSekvensIder, bigQueryGateway)
        bigQueryKontroll.validerForErstatning().fold(
            ifLeft = { return it.left() },
            ifRight = { },
        )

        bigQueryGateway.deleteExactlyOrVerifyMissing(validerteSekvensIder)
        bigQueryGateway.writeToBigQuery(data)
        bigQueryGateway.verifyExactlyOnce(validerteSekvensIder)

        logger.info("Erstattet ${data.size} rader i BigQuery-tabellen $TABLE")
        return ErstattetSakStatistikk(data.size).right()
    }

    override fun forhåndsvisErstattSakStatistikk(
        sekvensIder: List<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk, ForhåndsvisErstattSakStatistikk> {
        val validerteSekvensIder = when (val validering = validerSekvensIder(sekvensIder)) {
            is Either.Left -> return validering.value.left()
            is Either.Right -> validering.value
        }
        val postgresKontroll = kontrollerPostgres(sekvensIder, validerteSekvensIder)
        val bigQueryKontroll = kontrollerBigQuery(
            sekvensIder = sekvensIder,
            validerteSekvensIder = validerteSekvensIder,
            bigQueryGateway = bigQueryGateway,
        )

        return ForhåndsvisErstattSakStatistikk(
            kanErstattes = postgresKontroll.kanErstattes && bigQueryKontroll.kanErstattes,
            antallForespurte = sekvensIder.size,
            antallRaderISakStatistikk = postgresKontroll.data.size,
            antallRaderIBigQuery = bigQueryKontroll.antallRader,
            manglendeISakStatistikk = postgresKontroll.manglendeSekvensIder,
            ikkeUnikeISakStatistikk = postgresKontroll.ikkeUnikeSekvensIder,
        ).right()
    }

    private fun kontrollerPostgres(
        sekvensIder: List<Long>,
        validerteSekvensIder: Set<Long>,
    ): PostgresErstatningskontroll {
        val data = repo.hentSakStatistikk(validerteSekvensIder)
        val raderPerSekvensId = data.groupingBy { it.getSekvensId().longValueExact() }.eachCount()
        check(raderPerSekvensId.keys.all { it in validerteSekvensIder }) {
            "Repoet returnerte andre sekvens-ID-er enn de forespurte"
        }

        return PostgresErstatningskontroll(
            data = data,
            manglendeSekvensIder = sekvensIder.filter { raderPerSekvensId[it] == null },
            ikkeUnikeSekvensIder = sekvensIder.filter { (raderPerSekvensId[it] ?: 0) > 1 },
        )
    }

    private fun kontrollerBigQuery(
        sekvensIder: List<Long>,
        validerteSekvensIder: Set<Long>,
        bigQueryGateway: SakStatistikkBigQueryGateway,
    ): BigQueryErstatningskontroll {
        val raderPerSekvensId = bigQueryGateway.hentAntallRaderPerSekvensId(validerteSekvensIder)
        check(raderPerSekvensId.keys.all { it in validerteSekvensIder }) {
            "BigQuery returnerte andre sekvens-ID-er enn de forespurte"
        }
        return BigQueryErstatningskontroll(
            antallForespurte = sekvensIder.size,
            raderPerSekvensId = raderPerSekvensId,
        )
    }

    private fun validerSekvensIder(
        sekvensIder: List<Long>,
    ): Either<KunneIkkeErstatteSakStatistikk, Set<Long>> {
        if (sekvensIder.isEmpty()) {
            return KunneIkkeErstatteSakStatistikk.IngenSekvensIder.left()
        }
        if (sekvensIder.size > MAKS_ANTALL_SEKVENS_IDER) {
            return KunneIkkeErstatteSakStatistikk.ForMangeSekvensIder(MAKS_ANTALL_SEKVENS_IDER).left()
        }
        val ugyldigeSekvensIder = sekvensIder.filter { it <= 0 }.toSet()
        if (ugyldigeSekvensIder.isNotEmpty()) {
            return KunneIkkeErstatteSakStatistikk.UgyldigeSekvensIder(ugyldigeSekvensIder).left()
        }
        val duplikateSekvensIder = sekvensIder.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplikateSekvensIder.isNotEmpty()) {
            return KunneIkkeErstatteSakStatistikk.DuplikateSekvensIder(duplikateSekvensIder).left()
        }
        return sekvensIder.toSet().right()
    }
}

interface SakStatistikkBigQueryGateway {
    fun hentAntallRaderPerSekvensId(sekvensIder: Set<Long>): Map<Long, Long>
    fun deleteExactlyOrVerifyMissing(sekvensIder: Set<Long>)
    fun writeToBigQuery(data: List<SakStatistikk>)
    fun verifyExactlyOnce(sekvensIder: Set<Long>)

    companion object {
        fun forNais(): SakStatistikkBigQueryGateway = SakStatistikkBigQueryGatewayImpl()
        fun inMemory(): SakStatistikkBigQueryGateway = SakStatistikkBigQueryGatewayInMemory()
    }
}

private class SakStatistikkBigQueryGatewayInMemory : SakStatistikkBigQueryGateway {
    private val rader = mutableMapOf<Long, SakStatistikk>()

    @Synchronized
    override fun hentAntallRaderPerSekvensId(sekvensIder: Set<Long>): Map<Long, Long> =
        sekvensIder.filter { it in rader }.associateWith { 1L }

    @Synchronized
    override fun deleteExactlyOrVerifyMissing(sekvensIder: Set<Long>) {
        sekvensIder.forEach { rader.remove(it) }
    }

    @Synchronized
    override fun writeToBigQuery(data: List<SakStatistikk>) {
        data.forEach { rader[it.getSekvensId().longValueExact()] = it }
    }

    @Synchronized
    override fun verifyExactlyOnce(sekvensIder: Set<Long>) {
        check(sekvensIder.all { it in rader }) {
            "In-memory BigQuery mangler forventede sekvens-ID-er"
        }
    }
}

private class SakStatistikkBigQueryGatewayImpl : SakStatistikkBigQueryGateway {
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
            DECLARE antall_rader_foer INT64;
            DECLARE antall_valgte_rader INT64;
            DECLARE antall_valgte_ider INT64;

            BEGIN
              BEGIN TRANSACTION;

              SET antall_rader_foer = (SELECT COUNT(*) FROM $fullTableName);
              SET antall_valgte_rader = (
                SELECT COUNT(*)
                FROM $fullTableName
                WHERE id IN UNNEST(@sekvensIder)
              );
              SET antall_valgte_ider = (
                SELECT COUNT(DISTINCT id)
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

                ASSERT (SELECT COUNT(*) FROM $fullTableName) = antall_rader_foer - @forventetAntall
                AS 'Totalt radantall ble redusert med et uventet antall';
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
            ASSERT (
              SELECT COUNT(*)
              FROM $fullTableName
              WHERE id IN UNNEST(@sekvensIder)
            ) = @forventetAntall
            AS 'Ikke alle sekvens-ID-ene finnes nøyaktig én gang etter opplasting';

            ASSERT (
              SELECT COUNT(DISTINCT id)
              FROM $fullTableName
              WHERE id IN UNNEST(@sekvensIder)
            ) = @forventetAntall
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
