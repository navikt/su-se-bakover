package no.nav.su.se.bakover.database.sak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test

internal class OppdaterFødselsnummerPåSakRepoTest {
    @Test
    fun `oppdater fødselsnummer på sak`() {
        val gammeltFnr = Fnr(fnr = "11111111111")
        val nyttFnr = Fnr(fnr = "22222222222")
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.sakRepo
            val sak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave(
                fnr = gammeltFnr,
            )
            repo.hentSak(sak.id)!!.fnr shouldBe gammeltFnr
            repo.oppdaterFødselsnummer(
                sakId = sak.id,
                gammeltFnr = gammeltFnr,
                nyttFnr = nyttFnr,
                endretAv = saksbehandler,
                endretTidspunkt = Tidspunkt.now(testDataHelper.clock),
            )
            repo.hentSak(sak.id)!!.fnr shouldBe nyttFnr
        }
    }
}
