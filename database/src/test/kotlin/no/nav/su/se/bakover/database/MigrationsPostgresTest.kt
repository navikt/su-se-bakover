package no.nav.su.se.bakover.database

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class MigrationsPostgresTest {

    @Test
    fun `migreringer skal kjøre på en tom database`() {
        EmbeddedDatabase.instance().also {
            clean(it)
            val migrations: Int = Flyway(it, "postgres").migrate().migrationsExecuted
            migrations shouldBeGreaterThan 0
        }
    }

    @Test
    fun `migreringer skal ikke kjøre flere ganger`() {
        EmbeddedDatabase.instance().also {
            clean(it)
            val firstMigration = Flyway(it, "postgres").migrate().migrationsExecuted
            firstMigration shouldBeGreaterThan 0
            val sesondMigration = Flyway(it, "postgres").migrate().migrationsExecuted
            sesondMigration shouldBe 0
        }
    }
}
