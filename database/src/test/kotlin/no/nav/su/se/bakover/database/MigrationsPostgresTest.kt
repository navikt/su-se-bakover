package no.nav.su.se.bakover.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MigrationsPostgresTest {

    @Test
    fun `migreringer skal kjøre på en tom database`() {
        EmbeddedDatabase.database.also {
            clean(it)
            val migrations = Flyway(it, "postgres").migrate()
            assertEquals(2, migrations)
        }
    }

    @Test
    fun `migreringer skal ikke kjøre flere ganger`() {
        EmbeddedDatabase.database.also {
            clean(it)
            assertEquals(2, Flyway(it, "postgres").migrate())
            assertEquals(0, Flyway(it, "postgres").migrate())
        }
    }
}
