package no.nav.su.se.bakover.datapakker.soknad

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.QueryJobConfiguration
import no.nav.su.se.bakover.database.Postgres
import no.nav.su.se.bakover.database.VaultPostgres
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("DatapakkerSøknad")

fun main() {
    VaultPostgres(
        jdbcUrl = "jdbc:postgresql://b27dbvl009.preprod.local:5432/supstonad-db-dev",
        vaultMountPath = "postgresql/preprod-fss/",
        databaseName = "supstonad-db-dev",
    ).getDatasource(Postgres.Role.ReadOnly)

    val bigqueryJsonKey = System.getenv("bigquery")
    logger.info("This is hello from ${logger.name}. Vi har vault=${!bigqueryJsonKey.isNullOrEmpty()}")

    writeToBigQuery(bigqueryJsonKey)
}

fun writeToBigQuery(
    jsonKey: String,
    project: String = "supstonad-dev-0e48",
    dataset: String = "test",
    table: String = "testtable",
) {
    val credentials = GoogleCredentials.fromStream(jsonKey.byteInputStream())

    val bq: BigQuery =
        BigQueryOptions.newBuilder().setCredentials(credentials).setLocation("europe-north1").setProjectId(project)
            .build().service

    val query = QueryJobConfiguration.newBuilder(
        "insert into `$project.$dataset.$table` (kvasirdu) values('hilser.')",
    ).setUseLegacySql(false).build()

    val result = bq.query(query)
    println("inserted ${result.totalRows} rows into bigquery")
}
