package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class SøknadsbehandlingServiceBrevTest {
    private val tilAttesteringInnvilget = søknadsbehandlingTilAttesteringInnvilget().second
    private val uavklart = søknadsbehandlingVilkårsvurdertUavklart().second

    @Test
    fun `svarer med feil hvis vi ikke finner person`() {
        val brevServiceMock = mock<BrevService>() {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeHentePerson.left()
        }

        SøknadsbehandlingServiceAndMocks(
            brevService = brevServiceMock,
        ).let {
            it.søknadsbehandlingService.brev(SøknadsbehandlingService.BrevRequest.UtenFritekst(tilAttesteringInnvilget)) shouldBe KunneIkkeLageDokument.KunneIkkeHentePerson.left()
            verify(it.brevService).lagDokument(tilAttesteringInnvilget)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil hvis vi ikke finner navn på attestant eller saksbehandler`() {
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
        }

        val brevServiceMock = mock<BrevService>() {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
        }

        SøknadsbehandlingServiceAndMocks(
            personService = personServiceMock,
            brevService = brevServiceMock,
        ).let {
            it.søknadsbehandlingService.brev(SøknadsbehandlingService.BrevRequest.UtenFritekst(tilAttesteringInnvilget)) shouldBe KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
            verify(it.brevService).lagDokument(tilAttesteringInnvilget)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil hvis generering av pdf feiler`() {
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
        }

        val brevServiceMock = mock<BrevService>() {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
        }

        SøknadsbehandlingServiceAndMocks(
            personService = personServiceMock,
            brevService = brevServiceMock,
        ).let {
            it.søknadsbehandlingService.brev(SøknadsbehandlingService.BrevRequest.UtenFritekst(tilAttesteringInnvilget)) shouldBe KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
            verify(it.brevService).lagDokument(tilAttesteringInnvilget)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med byte array dersom alt går fint`() {
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(fnr, aktørId).right()
        }

        val pdf = "".toByteArray()
        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn Dokument.UtenMetadata.Vedtak(
                opprettet = fixedTidspunkt,
                tittel = "tittel1",
                generertDokument = pdf,
                generertDokumentJson = "{}",
            ).right()
        }

        SøknadsbehandlingServiceAndMocks(
            personService = personServiceMock,
            brevService = brevServiceMock,
        ).let {
            it.søknadsbehandlingService.brev(SøknadsbehandlingService.BrevRequest.UtenFritekst(tilAttesteringInnvilget)) shouldBe pdf.right()
            verify(it.brevService).lagDokument(tilAttesteringInnvilget)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kaster exception hvis det ikke er mulig å opprette brev for aktuell behandling`() {
        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doThrow LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(
                uavklart::class,
            )
        }

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            SøknadsbehandlingServiceAndMocks(
                brevService = brevServiceMock,
            ).søknadsbehandlingService.brev(
                SøknadsbehandlingService.BrevRequest.UtenFritekst(
                    uavklart,
                ),
            )
        }
    }
}
