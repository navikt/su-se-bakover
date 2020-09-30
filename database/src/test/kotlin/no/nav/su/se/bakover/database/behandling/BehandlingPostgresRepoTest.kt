package no.nav.su.se.bakover.database.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import org.junit.jupiter.api.Test

internal class BehandlingPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = BehandlingPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `opprett og hent behandling`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val søknad = testDataHelper.insertSøknad(sak.id)
            val behandling = testDataHelper.insertBehandling(sak.id, søknad)

            val hentet = repo.hentBehandling(behandling.id)

            behandling shouldBe hentet
        }
    }
}
