package no.nav.su.se.bakover.database.db.migration.v180

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.Flyway
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.database.DomainToQueryParameterMapper
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.sessionCounterStub
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import javax.sql.DataSource

internal class V180Test {
    @Test
    fun `rekkefølge blir entyding`() {
        withMigratedDb(dbMigrationVersion = 179) { ds: DataSource ->
            ds.connection.use {
                // Vi trenger ikke avstemming for denne testen sin del.
                it.createStatement().execute("alter table utbetaling drop constraint utbetaling_avstemmingid_fkey")
                it.createStatement().execute(
                    """
                    insert into sak (id, fnr, opprettet, saksnummer, type)
                    values  ('a53ad7db-9d1b-4121-8d39-c663a00f46e5', '30487835621', '2023-01-14 15:35:04.558614 +00:00', 10002098, 'uføre');
                    """.trimIndent(),
                )
                val utbetalinger = readFile("utbetalinger.sql")
                it.createStatement().execute(utbetalinger)

                val utbetalingslinjer = readFile("utbetalingslinjer.sql")
                it.createStatement().execute(utbetalingslinjer)
            }
            // Prøver kjører denne migreringen.
            Flyway(ds, "postgres").migrateTo(180)
            val sessionFactory = PostgresSessionFactory(
                dataSource = ds,
                dbMetrics = dbMetricsStub,
                sessionCounter = sessionCounterStub,
                queryParameterMappers = listOf(
                    DomainToQueryParameterMapper,
                ),
            )
            sessionFactory.withSession { session ->
                UtbetalingInternalRepo.hentOversendteUtbetalinger(
                    UUID.fromString("a53ad7db-9d1b-4121-8d39-c663a00f46e5"),
                    session,
                ).also {
                    it.size shouldBe 20
                    it.forEach {
                        it.utbetalingslinjer.map { it.rekkefølge }.size shouldBe it.utbetalingslinjer.size
                    }
                }
                val linjer = UtbetalingInternalRepo.hentUtbetalingslinjer(
                    UUID30.fromString("8bb94945-a264-478e-b884-599298"),
                    session,
                )
                linjer.size shouldBe 308
                linjer.sortedBy { it.rekkefølge }.also {
                    it.first().rekkefølge shouldBe Rekkefølge.start()
                    it[200].rekkefølge shouldBe Rekkefølge.skip(199)
                    it.last().rekkefølge shouldBe Rekkefølge.skip(306)
                }
            }
        }
    }

    private fun readFile(filename: String): String {
        return Files.readString(Paths.get("""src/test/kotlin/no/nav/su/se/bakover/database/db/migration/v180/$filename"""))
    }
}
