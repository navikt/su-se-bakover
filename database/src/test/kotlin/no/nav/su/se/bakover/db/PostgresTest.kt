package no.nav.su.se.bakover.db

import no.nav.su.se.bakover.database.NonVaultPostgres
import no.nav.su.se.bakover.database.Postgres
import no.nav.su.se.bakover.database.VaultPostgres
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class PostgresTest {

    @Test
    internal fun `bygger riktig datasource basert på vaultMountPath`() {
        assertTrue(
                Postgres(
                        jdbcUrl = "postgresql://localhost",
                        vaultMountPath = "",
                        username = "postgres",
                        password = "postgres",
                        databaseName = "dbName"
                )
                        .build() is NonVaultPostgres
        )
        assertTrue(
                Postgres(
                        jdbcUrl = "postgresql://localhost",
                        vaultMountPath = "aVaultPath",
                        username = "postgres",
                        password = "postgres",
                        databaseName = "dbName"
                ).build() is VaultPostgres
        )
    }

    @Test
    internal fun `kaster ikke exception når tilkobling konfigureres riktig`() {
        assertDoesNotThrow {
            Postgres(
                    jdbcUrl = "postgresql://localhost",
                    vaultMountPath = "aVaultPath",
                    username = "",
                    password = "",
                    databaseName = "dbName"
            ).build()
        }

        assertDoesNotThrow {
            Postgres(
                    jdbcUrl = "postgresql://localhost",
                    vaultMountPath = "",
                    username = "postgres",
                    password = "postgres",
                    databaseName = ""
            ).build()
        }
    }
}
