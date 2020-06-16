package no.nav.su.se.bakover.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MigrationsPostgresTest {

    @Test
    fun `migreringer skal kjøre på en tom database`() {
        EmbeddedDatabase.database.also {
            clean(it)
            val migrations = migrate(it)
            assertEquals(2, migrations)
        }
    }

    @Test
    fun `migreringer skal ikke kjøre flere ganger`() {
        EmbeddedDatabase.database.also {
            clean(it)
            assertEquals(2, migrate(it))
            assertEquals(0, migrate(it))
        }
    }
}
