package no.nav.su.se.bakover.db

import no.nav.su.se.bakover.EmbeddedPostgres
import no.nav.su.se.bakover.Postgres
import no.nav.su.se.bakover.VaultPostgres
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
                .build() is EmbeddedPostgres
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
    // TODO: det bør være en sanity sjekk ved oppstart på at vi har en konfigurasjon som gir oss en av de to databasene
}
