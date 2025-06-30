package no.nav.su.se.bakover.database.statistikk

import no.nav.su.se.bakover.test.persistence.withMigratedDb
import org.junit.jupiter.api.Test

internal class StønadRepoPostgresTest {
    @Test
    fun `Skal klare å lagre ting her...`() {
        withMigratedDb { dataSource ->
            // TODO
        }
    }
}
