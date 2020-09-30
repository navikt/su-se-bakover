package no.nav.su.se.bakover.database.søknad

import io.kotest.matchers.shouldBe
import kotliquery.using
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.database.withMigratedDb
import org.junit.jupiter.api.Test

internal class SøknadPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = SøknadPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `opprett og hent søknad`() {
        withMigratedDb {
            using(sessionOf(EmbeddedDatabase.instance())) {
                val sak = testDataHelper.insertSak(FNR)
                val søknad = testDataHelper.insertSøknad(sak.id)
                val hentet = repo.hentSøknad(søknad.id)

                søknad shouldBe hentet
            }
        }
    }
}
