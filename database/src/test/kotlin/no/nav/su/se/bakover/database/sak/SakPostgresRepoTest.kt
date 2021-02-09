package no.nav.su.se.bakover.database.sak

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.TestDataHelper
import no.nav.su.se.bakover.database.TestDataHelper.Companion.søknadNy
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Sak
import org.junit.jupiter.api.Test

internal class SakPostgresRepoTest {

    private val testDataHelper = TestDataHelper(EmbeddedDatabase.instance())
    private val repo = SakPostgresRepo(EmbeddedDatabase.instance(), mock(), mock())

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
}
