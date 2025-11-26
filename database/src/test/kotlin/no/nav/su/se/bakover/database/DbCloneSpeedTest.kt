package no.nav.su.se.bakover.database

import io.zonky.test.db.postgres.embedded.DatabasePreparer
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import io.zonky.test.db.postgres.embedded.PreparedDbProvider
import no.nav.su.se.bakover.common.infrastructure.persistence.Flyway
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

class DbCloneSpeedTest {

    class FlywayPreparer : DatabasePreparer {
        override fun prepare(ds: DataSource) {
            // Simulate your custom preparer
            Flyway(ds, "postgres").migrate()
        }
    }

    @Disabled
    @Test
    fun measureDbCloneSpeed() {
        println("Starting embedded postgres…")

        val templateTime = measureTimeMillis {
            PreparedDbProvider.forPreparer(FlywayPreparer())
        }

        println("Template creation (includes migrations): ${templateTime}ms")

        val provider = PreparedDbProvider.forPreparer(FlywayPreparer())

        val cloneTimes = (1..5).map {
            measureTimeMillis {
                provider.createNewDatabase()
            }
        }

        println("Database clone times: $cloneTimes")
        println("Average clone time: ${cloneTimes.average()} ms")

        // Compare to running Flyway manually without templating
        val embedded = EmbeddedPostgres.builder().start()

        val manualTimes = (1..3).map {
            val ds = embedded.postgresDatabase
            measureTimeMillis {
                Flyway(ds, "postgres").migrate()
            }
        }

        println("Manual Flyway migration times: $manualTimes")
        println("Average manual migration time: ${manualTimes.average()} ms")
    }
}
