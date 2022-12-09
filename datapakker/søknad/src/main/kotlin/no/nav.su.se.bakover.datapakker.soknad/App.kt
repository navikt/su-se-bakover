package no.nav.su.se.bakover.datapakker.soknad

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.FormatOptions
import com.google.cloud.bigquery.JobId
import com.google.cloud.bigquery.JobStatistics.LoadStatistics
import com.google.cloud.bigquery.TableId
import com.google.cloud.bigquery.WriteChannelConfiguration
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.database.Postgres
import no.nav.su.se.bakover.database.VaultPostgres
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.channels.Channels
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("DatapakkerSøknad")
private const val location = "europe-north1"

fun main() {
    val søknader = VaultPostgres(
        jdbcUrl = "jdbc:postgresql://b27dbvl009.preprod.local:5432/supstonad-db-dev",
        vaultMountPath = "postgresql/preprod-fss/",
        databaseName = "supstonad-db-dev",
    ).getDatasource(Postgres.Role.ReadOnly).let {
        it.use { hentSøknader(it) }
    }
    writeToBigQuery(søknader = søknader)
}

fun hentSøknader(datasource: DataSource): List<DatapakkeSøknad> {
    return datasource.connection.let {
        it.use {
            it.prepareStatement(
                """
            select
                id, 
                opprettet, 
                (søknadinnhold -> 'forNav' ->> 'type') as type,
                (søknadinnhold -> 'forNav' ->> 'mottaksdatoForSøknad') as mottaksdato
             from 
                søknad
                """.trimIndent(),
            ).executeQuery().let {
                val mutableList = mutableListOf<DatapakkeSøknad>()
                while (it.next()) {
                    val opprettet = it.getTimestamp("opprettet").toInstant().toTidspunkt()
                    mutableList.add(
                        DatapakkeSøknad(
                            id = UUID.fromString(it.getString("id")),
                            opprettet = opprettet,
                            type = DatapakkeSøknadstype.stringToSøknadstype(it.getString("type")),
                            mottaksdato = it.getString("mottaksdato")?.let { LocalDate.parse(it) }
                                ?: opprettet.toLocalDate(ZoneId.of("Europe/Oslo")),
                        ),
                    )
                }
                logger.info("totalt antall søknader: ${mutableList.size}")
                mutableList.toList()
            }
        }
    }
}

fun writeToBigQuery(
    jsonKey: InputStream = FileInputStream(File("/var/run/secrets/nais.io/vault/bigquery")),
    project: String = "supstonad-dev-0e48",
    dataset: String = "soknad",
    table: String = "papirVsDigital",
    søknader: List<DatapakkeSøknad>,
) {
    val credentials = GoogleCredentials.fromStream(jsonKey)

    val bq: BigQuery =
        BigQueryOptions.newBuilder().setCredentials(credentials).setLocation(location).setProjectId(project)
            .build().service

    val jobId = JobId.newBuilder().setLocation(location).setJob(UUID.randomUUID().toString()).build()

    val configuration = WriteChannelConfiguration.newBuilder(
        TableId.of(project, dataset, table),
    ).setFormatOptions(FormatOptions.csv()).build()

    val job = bq.writer(jobId, configuration).let {
        it.use { channel ->
            Channels.newOutputStream(channel).use { os ->
                os.write(søknader.toCSV().toByteArray())
            }
        }
        it.job.waitFor()
    }

    println(job.getStatistics<LoadStatistics>().toString())
}
