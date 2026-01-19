package no.nav.su.se.bakover.service.statistikk

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FormatOptions
import com.google.cloud.bigquery.Job
import com.google.cloud.bigquery.JobId
import com.google.cloud.bigquery.JobStatistics
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.WriteChannelConfiguration
import org.slf4j.LoggerFactory
import statistikk.domain.StønadstatistikkMåned
import java.nio.channels.Channels
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object StønadBigQueryService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private const val LOCATION = "europe-north1"

    fun lastTilBigQuery(data: List<StønadstatistikkMåned>) {
        log.info("Sender ${data.size} rader for stønadstatistikk fra databasen")
        writeToBigQuery(data)
        log.info("Slutter jobb Stønadstatistikk")
    }

    private fun writeToBigQuery(data: List<StønadstatistikkMåned>) {
        /*
            https://docs.nais.io/persistence/bigquery/how-to/connect/?h=bigquery
            defaulty inject basert på yaml filens referanses
         */
        val project: String = System.getenv("GCP_TEAM_PROJECT_ID")

        val bq = createBigQueryClient(project)

        val stoenadtable = "stoenadstatistikk"
        val stoenadCSV = data.toCSV()
        log.info("Skriver ${stoenadCSV.length} bytes til BigQuery-tabell: $stoenadtable")

        val jobStoenad = writeCsvToBigQueryTable(
            bigQueryClient = bq,
            project = project,
            tableName = stoenadtable,
            csvData = stoenadCSV,
        )

        log.info("Saksstatistikkjobb: ${jobStoenad.getStatistics<JobStatistics.LoadStatistics>()}")
    }

    private fun createBigQueryClient(project: String): BigQuery =
        BigQueryOptions.newBuilder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .setLocation(LOCATION)
            .setProjectId(project)
            .build()
            .service

    val dataset = "statistikk"
    private fun writeCsvToBigQueryTable(
        bigQueryClient: BigQuery,
        project: String,
        tableName: String,
        csvData: String,
    ): Job {
        val jobId = JobId.newBuilder()
            .setLocation(LOCATION)
            .setJob(UUID.randomUUID().toString())
            .build()

        val tableId = TableId.of(project, dataset, tableName)

        log.info("Writing csv to bigquery. id: $jobId, project: $project, table: $tableId")

        val writeConfig = WriteChannelConfiguration.newBuilder(tableId)
            .setFormatOptions(FormatOptions.csv())
            .build()

        val writer = try {
            bigQueryClient.writer(jobId, writeConfig)
        } catch (e: Exception) {
            throw RuntimeException("BigQuery writer creation failed: ${e.message}", e)
        }

        try {
            writer.use { channel ->
                Channels.newOutputStream(channel).use { os ->
                    os.write(csvData.toByteArray())
                }
            }
        } catch (e: Exception) {
            log.error("Failed to write CSV data to BigQuery stream: ${e.message}", e)
            throw RuntimeException("Error during CSV write to BigQuery", e)
        }

        val job = writer.job
        job.waitFor()

        return job
    }
}

/*
Endrer du rekkefølgen her må det også gjenspeiles i bigquery
Rekkefølge i BQ:
       "id", "maned", "vedtaksdato", "personnummer", "vedtakFraOgMed", "vedtakTilOgMed", "sakId",
       "funksjonellTid", "tekniskTid", "stonadstype", "personnummerEps", "vedtakstype", "vedtaksresultat",
       "opphorsgrunn", "opphorsdato", "arsakStans", "behandlendeEnhetKode", "stonadsklassifisering", "sats",
       "utbetales", "fradragsum", "uforegrad", "alderspensjon", "alderspensjoneps", "arbeidsavklaringspenger",
       "arbeidsavklaringspengereps", "arbeidsinntekt", "arbeidsinntekteps", "omstillingsstonad", "omstillingsstonadeps",
       "avtalefestetpensjon", "avtalefestetpensjoneps", "avtalefestetpensjonprivat", "avtalefestetpensjonprivateps",
       "bidragetterekteskapsloven", "bidragetterekteskapsloveneps", "dagpenger", "dagpengereps",
       "fosterhjemsgodtgjorelse", "fosterhjemsgodtgjorelseeps", "gjenlevendepensjon", "gjenlevendepensjoneps",
       "introduksjonsstonad", "introduksjonsstonadeps", "kapitalinntekt", "kapitalinntekteps", "kontantstotte",
       "kontantstotteeps", "kvalifiseringsstonad", "kvalifiseringsstonadeps", "navytelsertillivsopphold",
       "navytelsertillivsoppholdeps", "offentligpensjon", "offentligpensjoneps", "privatpensjon",
       "privatpensjoneps", "sosialstonad", "sosialstonadeps", "statenslanekasse", "statenslanekasseeps",
       "supplerendestonad", "supplerendestonadeps", "sykepenger", "sykepengereps", "tiltakspenger",
       "tiltakspengereps", "ventestonad", "ventestonadeps", "uforetrygd", "uforetrygdeps", "forventetinntekt",
       "forventetinntekteps", "avkortingutenlandsopphold", "avkortingutenlandsoppholdeps",
       "underminsteniva", "underminstenivaeps", "annet", "anneteps", "lastetdato"
 */
fun List<StønadstatistikkMåned>.toCSV(): String {
    return buildString {
        for (dto in this@toCSV) {
            appendLine(
                listOf(
                    dto.id.toString(),
                    dto.måned.toString(),
                    dto.vedtaksdato.toString(),
                    dto.personnummer.toString(),
                    dto.vedtakFraOgMed.toString(),
                    dto.vedtakTilOgMed.toString(),
                    dto.sakId.toString(),
                    dto.funksjonellTid.toString(),
                    dto.tekniskTid.toString(),
                    dto.stonadstype.toString(),
                    dto.personNummerEps.toString(),
                    dto.vedtakstype.toString(),
                    dto.vedtaksresultat.toString(),
                    dto.opphorsgrunn.orEmpty(),
                    dto.opphorsdato?.toString().orEmpty(),
                    dto.årsakStans.orEmpty(),
                    dto.behandlendeEnhetKode,
                    dto.stonadsklassifisering.toString(),
                    dto.sats?.toString().orEmpty(),
                    dto.utbetales?.toString().orEmpty(),
                    dto.fradragSum?.toString().orEmpty(),
                    dto.uføregrad?.toString().orEmpty(),
                    dto.alderspensjon?.toString().orEmpty(),
                    dto.alderspensjonEps?.toString().orEmpty(),
                    dto.arbeidsavklaringspenger?.toString().orEmpty(),
                    dto.arbeidsavklaringspengerEps?.toString().orEmpty(),
                    dto.arbeidsinntekt?.toString().orEmpty(),
                    dto.arbeidsinntektEps?.toString().orEmpty(),
                    dto.omstillingsstønad?.toString().orEmpty(),
                    dto.omstillingsstønadEps?.toString().orEmpty(),
                    dto.avtalefestetPensjon?.toString().orEmpty(),
                    dto.avtalefestetPensjonEps?.toString().orEmpty(),
                    dto.avtalefestetPensjonPrivat?.toString().orEmpty(),
                    dto.avtalefestetPensjonPrivatEps?.toString().orEmpty(),
                    dto.bidragEtterEkteskapsloven?.toString().orEmpty(),
                    dto.bidragEtterEkteskapslovenEps?.toString().orEmpty(),
                    dto.dagpenger?.toString().orEmpty(),
                    dto.dagpengerEps?.toString().orEmpty(),
                    dto.fosterhjemsgodtgjørelse?.toString().orEmpty(),
                    dto.fosterhjemsgodtgjørelseEps?.toString().orEmpty(),
                    dto.gjenlevendepensjon?.toString().orEmpty(),
                    dto.gjenlevendepensjonEps?.toString().orEmpty(),
                    dto.introduksjonsstønad?.toString().orEmpty(),
                    dto.introduksjonsstønadEps?.toString().orEmpty(),
                    dto.kapitalinntekt?.toString().orEmpty(),
                    dto.kapitalinntektEps?.toString().orEmpty(),
                    dto.kontantstøtte?.toString().orEmpty(),
                    dto.kontantstøtteEps?.toString().orEmpty(),
                    dto.kvalifiseringsstønad?.toString().orEmpty(),
                    dto.kvalifiseringsstønadEps?.toString().orEmpty(),
                    dto.navYtelserTilLivsopphold?.toString().orEmpty(),
                    dto.navYtelserTilLivsoppholdEps?.toString().orEmpty(),
                    dto.offentligPensjon?.toString().orEmpty(),
                    dto.offentligPensjonEps?.toString().orEmpty(),
                    dto.privatPensjon?.toString().orEmpty(),
                    dto.privatPensjonEps?.toString().orEmpty(),
                    dto.sosialstønad?.toString().orEmpty(),
                    dto.sosialstønadEps?.toString().orEmpty(),
                    dto.statensLånekasse?.toString().orEmpty(),
                    dto.statensLånekasseEps?.toString().orEmpty(),
                    dto.supplerendeStønad?.toString().orEmpty(),
                    dto.supplerendeStønadEps?.toString().orEmpty(),
                    dto.sykepenger?.toString().orEmpty(),
                    dto.sykepengerEps?.toString().orEmpty(),
                    dto.tiltakspenger?.toString().orEmpty(),
                    dto.tiltakspengerEps?.toString().orEmpty(),
                    dto.ventestønad?.toString().orEmpty(),
                    dto.ventestønadEps?.toString().orEmpty(),
                    dto.uføretrygd?.toString().orEmpty(),
                    dto.uføretrygdEps?.toString().orEmpty(),
                    dto.forventetInntekt?.toString().orEmpty(),
                    dto.forventetInntektEps?.toString().orEmpty(),
                    dto.avkortingUtenlandsopphold?.toString().orEmpty(),
                    dto.avkortingUtenlandsoppholdEps?.toString().orEmpty(),
                    dto.underMinstenivå?.toString().orEmpty(),
                    dto.underMinstenivåEps?.toString().orEmpty(),
                    dto.annet?.toString().orEmpty(),
                    dto.annetEps?.toString().orEmpty(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm")),
                ).joinToString(",") { escapeCsv(it) },
            )
        }
    }
}

private fun escapeCsv(field: String): String {
    val needsQuotes = field.contains(",") || field.contains("\"") || field.contains("\n")
    val escaped = field.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}
