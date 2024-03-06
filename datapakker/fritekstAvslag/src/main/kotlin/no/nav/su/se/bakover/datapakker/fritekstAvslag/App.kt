package no.nav.su.se.bakover.datapakker.fritekstAvslag

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FormatOptions
import com.google.cloud.bigquery.JobId
import com.google.cloud.bigquery.JobStatistics
import com.google.cloud.bigquery.QueryJobConfiguration
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
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("DatapakkerFritekstAvslag")
private const val LOCATION = "europe-north1"

fun main() {
    val databaseUrl = System.getenv("DATABASE_JDBC_URL")
    val antallAvslagsvedtakUtenFritekst = VaultPostgres(
        jdbcUrl = databaseUrl,
        vaultMountPath = System.getenv("VAULT_MOUNTPATH"),
        databaseName = System.getenv("DATABASE_NAME"),
    ).getDatasource(Postgres.Role.ReadOnly).let {
        logger.info("Startet database med url: $databaseUrl")
        it.use { hentAntallAvslagsvedtakUtenFritekst(it) }
    }

    deleteAllAndWriteToBigQuery(antallAvslagsvedtakUtenFritekst = antallAvslagsvedtakUtenFritekst)
}

fun hentAntallAvslagsvedtakUtenFritekst(datasource: DataSource): List<AvslagsvedtakUtenFritekst> {
    return datasource.connection.let {
        it.use {
            it.prepareStatement(
                """
                    select count(d.generertdokumentjson), to_char(date_trunc('month', v.opprettet), 'YYYY-MM') as grupperingsdato
                    from vedtak v
                             join dokument d on v.id = d.vedtakid
                    where length(trim(d.generertdokumentjson ->> 'fritekst')) < 1
                      and v.vedtaktype = 'AVSLAG'
                    group by grupperingsdato;
                """.trimIndent(),
            ).executeQuery().let {
                val result = mutableListOf<AvslagsvedtakUtenFritekst>()

                while (it.next()) {
                    result.add(
                        AvslagsvedtakUtenFritekst(
                            antall = it.getInt("count"),
                            forMånedÅr = YearMonth.parse(it.getString("grupperingsdato")),
                        ),
                    )
                }

                result.toList()
            }
        }
    }
}

fun deleteAllAndWriteToBigQuery(
    jsonKey: InputStream = FileInputStream(File("/var/run/secrets/nais.io/vault/bigquery")),
    project: String = System.getenv("GCP_PROJECT"),
    dataset: String = "avslagsvedtak",
    table: String = "antallAvslagsvedtakUtenFritekst",
    antallAvslagsvedtakUtenFritekst: List<AvslagsvedtakUtenFritekst>,
) {
    val credentials = GoogleCredentials.fromStream(jsonKey)

    val bq = BigQueryOptions
        .newBuilder()
        .setCredentials(credentials)
        .setLocation(LOCATION)
        .setProjectId(project)
        .build().service

    deleteAll(bq)

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

    logger.info("job statistikk: ${job.getStatistics<JobStatistics.LoadStatistics>()}")
}

fun deleteAll(
    bq: BigQuery,
    dataset: String = "avslagsvedtak",
    table: String = "antallAvslagsvedtakUtenFritekst",
) {
    val query = QueryJobConfiguration.newBuilder(
        "DELETE FROM `$dataset.$table` WHERE true",
    ).setUseLegacySql(false).build()

    val result = bq.query(query)
    logger.info("slettet antall linjer ${result.totalRows}")
}
