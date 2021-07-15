package no.nav.su.se.bakover.database.sak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.TestDataHelper.Companion.søknadNy
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import org.junit.jupiter.api.Test

internal class SakPostgresRepoTest {

    private val testDataHelper = TestDataHelper()
    private val repo = testDataHelper.sakRepo

    @Test
    fun `opprett og hent sak`() {
        withMigratedDb {
            val nySak = testDataHelper.nySakMedNySøknad()
            val opprettet: Sak = repo.hentSak(nySak.fnr)!!
            val hentetId = repo.hentSak(opprettet.id)!!
            val hentetFnr = repo.hentSak(opprettet.fnr)!!

            opprettet shouldBe hentetId
            hentetId shouldBe hentetFnr

            opprettet.fnr shouldBe nySak.fnr
            opprettet.id shouldBe nySak.id
            opprettet.opprettet shouldBe nySak.opprettet
            opprettet.søknadNy() shouldBe nySak.søknad
        }
    }

    @Test
    fun `oppretter 3 saker, og henter alle`() {
        withMigratedDb {
            testDataHelper.nySakMedNySøknad()
            testDataHelper.nySakMedNySøknad()
            testDataHelper.nySakMedNySøknad()

            val alleSaker = repo.hentAlleSaker()

            alleSaker.size shouldBe 3
            alleSaker[0].saksnummer shouldBe Saksnummer(2021)
            alleSaker[1].saksnummer shouldBe Saksnummer(2022)
            alleSaker[2].saksnummer shouldBe Saksnummer(2023)
        }
    }
}
