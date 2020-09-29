package no.nav.su.se.bakover.database.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import org.junit.jupiter.api.Test

internal class BeregningPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = BeregningPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `hent beregning`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val behandling = testDataHelper.insertBehandling(sak.id, søknad)
            val beregning = testDataHelper.insertBeregning(behandling.id)

            val hentet = repo.hentBeregning(beregning.id)

            hentet shouldBe beregning
        }
    }
}
