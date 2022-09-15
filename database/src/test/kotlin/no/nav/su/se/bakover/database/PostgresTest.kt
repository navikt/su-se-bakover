package no.nav.su.se.bakover.database

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.ApplicationConfig
import org.junit.jupiter.api.Test

internal class PostgresTest {

    @Test
    internal fun `bygger riktig dataSource basert på vaultMountPath`() {
        Postgres(
            ApplicationConfig.DatabaseConfig.StaticCredentials(
                jdbcUrl = "postgresql://localhost",
            ),
        ).build().shouldBeTypeOf<NonVaultPostgres>()

        Postgres(
            ApplicationConfig.DatabaseConfig.RotatingCredentials(
                jdbcUrl = "postgresql://localhost",
                vaultMountPath = "aVaultPath",
                databaseName = "dbName",
            ),
        ).build().shouldBeTypeOf<VaultPostgres>()
    }

    @Test
    internal fun `kaster ikke exception når tilkobling konfigureres riktig`() {
        shouldNotThrowAny {
            Postgres(
                ApplicationConfig.DatabaseConfig.RotatingCredentials(
                    jdbcUrl = "postgresql://localhost",
                    vaultMountPath = "aVaultPath",
                    databaseName = "dbName",
                ),
            ).build()
        }

        shouldNotThrowAny {
            Postgres(
                ApplicationConfig.DatabaseConfig.StaticCredentials(
                    jdbcUrl = "postgresql://localhost",
                ),
            ).build()
        }
    }
}
