package no.nav.su.se.bakover.database.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import org.junit.jupiter.api.Test

internal class OppdragPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = OppdragPostgresRepo(EmbeddedDatabase.instance())

    @Test
    fun `hent oppdrag for sak`() {
        withMigratedDb {
            val sak = testDataHelper.insertSak(FNR)
            val oppdrag = repo.hentOppdrag(sak.id)
            oppdrag shouldBe sak.oppdrag
        }
    }
}
