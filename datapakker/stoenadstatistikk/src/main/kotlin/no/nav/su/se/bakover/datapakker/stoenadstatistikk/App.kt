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
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.su.se.bakover.database.Postgres
import no.nav.su.se.bakover.database.VaultPostgres
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.channels.Channels
import java.time.YearMonth
import java.util.UUID
import javax.sql.DataSource

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

private fun <T> String.hentListe(
    params: Map<String, Any> = emptyMap(),
    session: Session,
    rowMapping: (Row) -> T,
): List<T> {
    return session.run(queryOf(this, params).map { row -> rowMapping(row) }.asList)
}

fun hentData(dataSource: DataSource, måned: YearMonth): List<StønadstatistikkMånedDto> {
    return dataSource.connection.use {
        val session = sessionOf(dataSource)
        """
        SELECT *
        FROM stoenad_maaned_statistikk
        WHERE maaned = :maaned
        """.trimIndent()
            .hentListe(
                params = mapOf("maaned" to måned.atDay(1)),
                session = session,
            ) { row ->
                with(row) {
                    val id = uuid("id")
                    StønadstatistikkMånedDto(
                        id = id,
                        måned = måned,
                        funksjonellTid = string("funksjonell_tid"),
                        tekniskTid = string("teknisk_tid"),
                        sakId = UUID.fromString(string("sak_id")),
                        stonadstype = string("stonadstype"),
                        vedtaksdato = localDate("vedtaksdato"),
                        personnummer = string("personnummer"),
                        personNummerEps = stringOrNull("personnummer_eps"),
                        vedtakFraOgMed = localDate("vedtak_fra_og_med"),
                        vedtakTilOgMed = localDate("vedtak_til_og_med"),
                        vedtakstype = string("vedtakstype"),
                        vedtaksresultat = string("vedtaksresultat"),
                        opphorsgrunn = stringOrNull("opphorsgrunn"),
                        opphorsdato = localDateOrNull("opphorsdato"),
                        behandlendeEnhetKode = string("behandlende_enhet_kode"),
                        harUtenlandsOpphold = stringOrNull("har_utenlandsopphold"),
                        harFamiliegjenforening = stringOrNull("har_familiegjenforening"),
                        flyktningsstatus = stringOrNull("flyktningsstatus"),
                        månedsbeløp = hentMånedsbeløp(session, id).singleOrNull(),
                    )
                }
            }
    }
}

private fun hentMånedsbeløp(session: Session, stoenadStatistikkId: UUID): List<Månedsbeløp> {
    return """
        SELECT id, maaned, stonadsklassifisering, sats, utbetales, fradrag_sum, uforegrad 
        FROM manedsbelop_statistikk
        WHERE stoenad_statistikk_id = :stoenad_statistikk_id
    """.trimIndent()
        .hentListe(
            params = mapOf("stoenad_statistikk_id" to stoenadStatistikkId),
            session = session,
        ) { row ->
            val manedsbelopId = UUID.fromString(row.string("id"))
            val maaned = row.string("maaned")
            val stonadsklassifisering = row.string("stonadsklassifisering")
            val sats = row.long("sats")
            val utbetales = row.long("utbetales")
            val fradragSum = row.long("fradrag_sum")
            val uføregrad = row.intOrNull("uforegrad")

            Månedsbeløp(
                manedsbelopId = manedsbelopId.toString(),
                måned = maaned,
                stonadsklassifisering = stonadsklassifisering,
                sats = sats,
                utbetales = utbetales,
                fradragSum = fradragSum,
                uføregrad = uføregrad,
                fradrag = hentInntekter(session, manedsbelopId),
            )
        }
}

private fun hentInntekter(session: Session, manedsbelop_id: UUID): List<Fradrag> {
    return """
            SELECT fradragstype, belop, tilhorer, er_utenlandsk
            FROM fradrag_statistikk 
            WHERE manedsbelop_id = :manedsbelop_id
    """.trimIndent()
        .hentListe(
            params = mapOf("manedsbelop_id" to manedsbelop_id),
            session = session,
        ) { row ->
            Fradrag(
                fradragstype = row.string("fradragstype"),
                beløp = row.long("belop"),
                tilhører = row.string("tilhorer"),
                erUtenlandsk = row.boolean("er_utenlandsk"),
            )
        }
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
