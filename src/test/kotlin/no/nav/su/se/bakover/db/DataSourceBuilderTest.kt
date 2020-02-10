package no.nav.su.se.bakover.db

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

internal class DataSourceBuilderTest {

    @Test
    internal fun `bygger riktig datasource basert på vaultMountPath`() {
        val env = mapOf(
                "DATABASE_JDBC_URL" to "postgresql://localhost",
                "DATABASE_USERNAME" to "postgres",
                "DATABASE_PASSWORD" to "postgres",
                "DATABASE_NAME" to "dbName"
        )
        assertTrue(DataSourceBuilder(env + mapOf("VAULT_MOUNTPATH" to "")).build() is EmbeddedPostgres)
        assertTrue(DataSourceBuilder(env + mapOf("VAULT_MOUNTPATH" to "theREALPath")).build() is VaultPostgres)
    }

    @Test
    internal fun `kaster ikke exception når tilkobling konfigureres riktig`() {
        assertDoesNotThrow {
            DataSourceBuilder(mapOf(
                    "DATABASE_JDBC_URL" to "foobar",
                    "VAULT_MOUNTPATH" to "foobar",
                    "DATABASE_NAME" to "foobar"
            )).build()
        }

        assertDoesNotThrow {
            DataSourceBuilder(mapOf(
                    "DATABASE_JDBC_URL" to "foobar",
                    "VAULT_MOUNTPATH" to "",
                    "DATABASE_USERNAME" to "foobar",
                    "DATABASE_PASSWORD" to "foobar"
            )).build()
        }
    }

    @Test
    internal fun `kaster exception ved mangende konfig`() {
        assertThrows<IllegalStateException> {
            DataSourceBuilder(emptyMap()).build()
        }

        assertThrows<IllegalStateException> {
            DataSourceBuilder(mapOf(
                    "DATABASE_NAME" to "foobar",
                    "DATABASE_USERNAME" to "foobar"
            )
            ).build()
        }

        assertThrows<IllegalStateException> {
            DataSourceBuilder(mapOf(
                    "DATABASE_JDBC_URL" to "foobar",
                    "VAULT_MOUNTPATH" to "foobar"
            )).build()
        }

        assertThrows<IllegalStateException> {
            DataSourceBuilder(mapOf(
                    "VAULT_MOUNTPATH" to "foobar",
                    "DATABASE_USERNAME" to "foobar"
            )).build()
        }

        assertThrows<IllegalStateException> {
            DataSourceBuilder(mapOf(
                    "VAULT_MOUNTPATH" to "",
                    "DATABASE_PASSWORD" to "foobar"
            )).build()
        }

        assertThrows<IllegalStateException> {
            DataSourceBuilder(mapOf(
                    "DATABASE_JDBC_URL" to "foobar",
                    "DATABASE_PASSWORD" to "foobar",
                    "VAULT_MOUNTPATH" to ""
            )).build()
        }
    }
}
