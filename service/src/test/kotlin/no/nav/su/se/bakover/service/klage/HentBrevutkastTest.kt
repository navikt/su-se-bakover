package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.klage.Hjemler
import no.nav.su.se.bakover.domain.klage.Hjemmel
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.ZoneOffset
import java.util.UUID

internal class HentBrevutkastTest {

    @Test
    fun `fant ikke klage`() {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")
        val klageId = UUID.randomUUID()
        mocks.service.brevutkast(
            klageId = klageId,
            saksbehandler = saksbehandler,
            fritekst = "Dette er friteksten til brevet.",
            hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)),
        ) shouldBe KunneIkkeLageBrevutkast.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke knyttet vedtak`() {

        val klage = påbegyntVurdertKlage().second

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn null
            },
            microsoftGraphApiMock = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Ole Nordmann".right()
            },
        )
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = "Dette er friteksten til brevet.",
            hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)),
        ) shouldBe KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(KunneIkkeLageBrevForKlage.FantIkkeVedtakKnyttetTilKlagen)
            .left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.microsoftGraphApiMock).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke saksbehandler`() {
        val klage = påbegyntVurdertKlage().second

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
            microsoftGraphApiMock = mock {
                on { hentNavnForNavIdent(any()) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            },
        )
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = "Dette er friteksten til brevet.",
            hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)),
        ) shouldBe KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(KunneIkkeLageBrevForKlage.FantIkkeSaksbehandler)
            .left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.microsoftGraphApiMock).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke person`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val vedtak = sak.vedtakListe.first()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            microsoftGraphApiMock = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Ole Nordmann".right()
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
        )
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = "Dette er friteksten til brevet.",
            hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)),
        ) shouldBe KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(KunneIkkeLageBrevForKlage.FantIkkePerson).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.microsoftGraphApiMock).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `generering av brev feilet`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val vedtak = sak.vedtakListe.first()
        val person = person(fnr = sak.fnr)

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            microsoftGraphApiMock = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Ola Nordmann".right()
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            brevServiceMock = mock {
                on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
            },
        )

        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        val fritekstTilBrev = "Dette er friteksten til brevet."
        val hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3))
        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = fritekstTilBrev,
            hjemler = hjemler,
        ) shouldBe KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.microsoftGraphApiMock).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Ola Nordmann",
                    fritekst = fritekstTilBrev,
                    hjemler = nonEmptyListOf(3),
                    klageDato = 1.desember(2021),
                    vedtakDato = 1.januar(2021),
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne hente brevutkast til klage`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val vedtak = sak.vedtakListe.first()
        val person = person(fnr = sak.fnr)
        val pdfAsBytes = "brevbytes".toByteArray()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            microsoftGraphApiMock = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Ola Nordmann".right()
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            brevServiceMock = mock {
                on { lagBrev(any()) } doReturn pdfAsBytes.right()
            },
        )
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        val fritekstTilBrev = "Dette er friteksten til brevet."
        val hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3))
        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = fritekstTilBrev,
            hjemler = hjemler,
        ) shouldBe pdfAsBytes.right()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.microsoftGraphApiMock).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Ola Nordmann",
                    fritekst = fritekstTilBrev,
                    hjemler = nonEmptyListOf(3),
                    klageDato = 1.desember(2021),
                    vedtakDato = 1.januar(2021),
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
