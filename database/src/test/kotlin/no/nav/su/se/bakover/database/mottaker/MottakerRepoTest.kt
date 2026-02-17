package no.nav.su.se.bakover.database.mottaker

import dokument.domain.distribuering.Distribueringsadresse
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.mottaker.BrevtypeMottaker
import no.nav.su.se.bakover.domain.mottaker.MottakerFnrDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class MottakerRepoTest(private val dataSource: DataSource) {

    @Test
    fun `lagrer og henter mottaker`() {
        val testDataHelper = TestDataHelper(dataSource)
        val repo = testDataHelper.mottakerRepo
        val referanseId = UUID.randomUUID()
        val referanseType = ReferanseTypeMottaker.REVURDERING
        val sak: NySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()

        val mottaker = MottakerFnrDomain(
            navn = "tester",
            foedselsnummer = sak.fnr,
            adresse = Distribueringsadresse(
                adresselinje1 = "adresselinje2",
                adresselinje2 = null,
                adresselinje3 = null,
                postnummer = "postnummer",
                poststed = "poststed",
            ),
            sakId = sak.id,
            referanseId = referanseId,
            referanseType = referanseType,
            brevtype = BrevtypeMottaker.VEDTAKSBREV,
        )
        repo.lagreMottaker(mottaker)
        repo.lagreMottaker(mottaker.copy(referanseId = UUID.randomUUID(), id = UUID.randomUUID())) // For å sjekke at ting går fint med en random annen ref
        val ident = MottakerIdentifikator(referanseType, referanseId, BrevtypeMottaker.VEDTAKSBREV)
        val hentetMottaker = repo.hentMottaker(ident)
        hentetMottaker shouldBe mottaker
        val nyMottaker = mottaker.copy(navn = "ny person")
        repo.oppdaterMottaker(nyMottaker)
        val hentetNymottaker = repo.hentMottaker(ident)
        hentetNymottaker shouldBe nyMottaker
        repo.slettMottaker(hentetNymottaker!!.id)
        repo.hentMottaker(ident) shouldBe null
    }
}
