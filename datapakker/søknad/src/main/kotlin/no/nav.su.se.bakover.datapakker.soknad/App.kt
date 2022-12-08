package no.nav.su.se.bakover.datapakker.soknad

import no.nav.su.se.bakover.database.Postgres
import no.nav.su.se.bakover.database.VaultPostgres
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("DatapakkerSøknad")

fun main() {
    VaultPostgres(
        jdbcUrl = "jdbc:postgresql://b27dbvl009.preprod.local:5432/supstonad-db-dev",
        vaultMountPath = "postgresql/preprod-fss/",
        databaseName = "supstonad-db-dev",
    ).getDatasource(Postgres.Role.ReadOnly).let {
        it.getConnection(it.username, it.password)
    }

    logger.info(
        """
        secret-env:${System.getProperty("secret-env")}
        """.trimIndent(),
    )

    logger.info("This is hello from ${logger.name}. Vi har vault=${!System.getProperty("bigquery", null).isNullOrEmpty()}")
}
