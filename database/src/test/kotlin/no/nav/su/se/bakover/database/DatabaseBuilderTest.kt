package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.db.EmbeddedDatabase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

internal class DatabaseBuilderTest {
    @Test
    fun `key validation`() {
        assertThrows<IllegalArgumentException> {
            DatabaseBuilder.fromEnv(mapOf(
                    "db.jdbcUrl" to EmbeddedDatabase.getEmbeddedJdbcUrl(),
                    "vaultMountPath" to "",
                    "db.name" to "postgres",
                    "db.username" to "postgres",
                    "db.password" to "postgres"
            ))
        }
        assertDoesNotThrow {
            DatabaseBuilder.fromEnv(mapOf(
                    "db.jdbcUrl" to EmbeddedDatabase.getEmbeddedJdbcUrl(),
                    "db.vaultMountPath" to "",
                    "db.name" to "postgres",
                    "db.username" to "postgres",
                    "db.password" to "postgres"
            ))
        }
    }
}