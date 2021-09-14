package no.nav.su.se.bakover.database

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class MigrationsPostgresTest {

    @Test
    fun `rader skal ikke lekke ut av withMigratedDb`() {
        withMigratedDb { dataSource ->
            TestDataHelper(dataSource).nySakMedJournalførtSøknadOgOppgave()
            dataSource.withSession { session ->
                "select count(1) from sak".antall(session = session) shouldBe 1
            }
        }
        withMigratedDb { dataSource ->
            dataSource.withSession { session ->
                "select count(1) from sak".antall(session = session) shouldBe 0
            }
        }
    }
}
