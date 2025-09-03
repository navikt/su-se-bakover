package no.nav.su.se.bakover.datapakker.stoenadstatistikk

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FormatOptions
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
                måned = maaned,
                stonadsklassifisering = stonadsklassifisering,
                sats = sats,
                utbetales = utbetales,
                fradrag = hentInntekter(session, manedsbelopId),
                fradragSum = fradragSum,
                uføregrad = uføregrad,
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

// TODO: Må vi slette innhold før vi oppdaterer eller kun endre til å kjøre en gang i måneden?
fun writeToBigQuery(
    data: List<StønadstatistikkMånedDto>,
) {
    val jsonKey: InputStream = FileInputStream(File(System.getenv("BIGQUERY_CREDENTIALS")))
    val project: String = System.getenv("GCP_PROJECT")

    val dataset = "statistikk"

    val credentials = GoogleCredentials.fromStream(jsonKey)
    val bq = BigQueryOptions
        .newBuilder()
        .setCredentials(credentials)
        .setLocation(LOCATION)
        .setProjectId(project)
        .build().service

    val jobIdStoenad = JobId.newBuilder().setLocation(LOCATION).setJob(UUID.randomUUID().toString()).build()

    val stoenadtable = "stoenadstatistikk"
    val configuration = WriteChannelConfiguration.newBuilder(
        TableId.of(project, dataset, stoenadtable),
    ).setFormatOptions(FormatOptions.csv()).build()

    val jobStoenadtable = bq.writer(jobIdStoenad, configuration).let {
        it.use { channel ->
            Channels.newOutputStream(channel).use { os ->
                os.write(data.toCSV().toByteArray()) // Denne legger inn alle vedtakene isolert uten månedsbeløp eller fradagsbeløp
            }
        }
        it.job.waitFor()
    }
    // TODO: Split CSV generation into seperate methods to make it testable?
    val månedstabell = "manedsbelop_statistikk"
    val configurationMåned = WriteChannelConfiguration.newBuilder(
        TableId.of(project, dataset, månedstabell),
    ).setFormatOptions(FormatOptions.csv()).build()

    val headerMåned = "måned,stonadsklassifisering,sats,utbetales,fradragSum,uføregrad,stoenad_statistikk_id\n"
    val alleMånedsBeløp = data.mapNotNull {
        it.månedsbeløp?.toCSV(it.id)
    }
    val csvContent = buildString {
        append(headerMåned)
        alleMånedsBeløp.forEach { append(it) }
    }

    val jobIdMaaned = JobId.newBuilder().setLocation(LOCATION).setJob(UUID.randomUUID().toString()).build()
    val maanedJob = bq.writer(jobIdMaaned, configurationMåned).let {
        it.use { channel ->
            Channels.newOutputStream(channel).use { os ->
                os.write(csvContent.toByteArray()) // Månedsbeløp for alle stønader for å kun gjøre en skrivejobb
            }
        }
        it.job.waitFor()
    }

    val fradragstabell = "fradrag_statistikk"
    val configurationMånedFradrag = WriteChannelConfiguration.newBuilder(
        TableId.of(project, dataset, fradragstabell),
    ).setFormatOptions(FormatOptions.csv()).build()

    val headerFradrag = "fradragstype,belop,tilhorer,erUtenlandsk,manedsbelop_id\n"
    val alleFradragsBeløp = data.map {
        it.månedsbeløp?.fradrag?.toCSV(it.id)
    }
    val csvContentFradrag = buildString {
        append(headerFradrag)
        alleFradragsBeløp.forEach { append(it) }
    }

    val jobIdFradrag = JobId.newBuilder().setLocation(LOCATION).setJob(UUID.randomUUID().toString()).build()
    val fradragjob = bq.writer(jobIdFradrag, configurationMånedFradrag).let {
        it.use { channel ->
            Channels.newOutputStream(channel).use { os ->
                os.write(csvContentFradrag.toByteArray()) // Månedsbeløp for alle stønader for å kun gjøre en skrivejobb
            }
        }
        it.job.waitFor()
    }

    logger.info("Stønadstatistikkjob - DTO: ${jobStoenadtable.getStatistics<JobStatistics.LoadStatistics>()}")
    logger.info("Stønadstatistikkjob - måned: ${maanedJob.getStatistics<JobStatistics.LoadStatistics>()}")
    logger.info("Stønadstatistikkjob - fradrag: ${fradragjob.getStatistics<JobStatistics.LoadStatistics>()}")
}
