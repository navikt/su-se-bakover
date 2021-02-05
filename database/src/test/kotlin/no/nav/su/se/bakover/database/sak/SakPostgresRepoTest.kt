package no.nav.su.se.bakover.database.sak

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.FnrGenerator
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Sak
import org.junit.jupiter.api.Test

internal class SakPostgresRepoTest {

    private val FNR = FnrGenerator.random()
    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = SakPostgresRepo(EmbeddedDatabase.instance(), mock())

    @Test
    fun `opprett og hent sak`() {
        withMigratedDb {
            testDataHelper.nySakMedNySøknad(FNR)
            val opprettet: Sak = repo.hentSak(FNR)!!
            val hentetId = repo.hentSak(opprettet.id)!!
            val hentetFnr = repo.hentSak(FNR)!!

            opprettet shouldBe hentetId
            hentetId shouldBe hentetFnr

            opprettet.fnr shouldBe FNR
        }
    }
}
