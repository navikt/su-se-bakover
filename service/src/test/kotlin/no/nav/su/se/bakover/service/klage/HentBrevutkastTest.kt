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
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
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

        val klageId = UUID.randomUUID()
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        mocks.service.brevutkast(
            sakId = UUID.randomUUID(),
            klageId = klageId,
            saksbehandler = saksbehandler,
            fritekst = "Dette er friteksten til brevet.",
            hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)),

        ) shouldBe KunneIkkeLageBrevutkast.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke sak`() {

        val klage = påbegyntVurdertKlage()
        val sakId = UUID.randomUUID()
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn null
            },
        )

        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        mocks.service.brevutkast(
            sakId = sakId,
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = "Dette er friteksten til brevet.",
            hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)),
        ) shouldBe KunneIkkeLageBrevutkast.FantIkkeSak.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe sakId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke knyttet vedtak`() {

        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()
        val klage = påbegyntVurdertKlage()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn null
            },
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
        )

        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        mocks.service.brevutkast(
            sakId = sak.id,
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = "Dette er friteksten til brevet.",
            hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)),

        ) shouldBe KunneIkkeLageBrevutkast.FantIkkeKnyttetVedtak.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe klage.sakId })
        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke saksbehandler`() {

        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val klage = påbegyntVurdertKlage()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
            },
            microsoftGraphApiMock = mock {
                on { hentNavnForNavIdent(any()) } doReturn MicrosoftGraphApiOppslagFeil.DeserialiseringAvResponsFeilet.left()
            },
        )

        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        mocks.service.brevutkast(
            sakId = sak.id,
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = "Dette er friteksten til brevet.",
            hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)),

        ) shouldBe KunneIkkeLageBrevutkast.FantIkkeSaksbehandler.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe klage.sakId })
        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.microsoftGraphApiMock).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke person`() {

        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val klage = påbegyntVurdertKlage()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
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
            sakId = sak.id,
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = "Dette er friteksten til brevet.",
            hjemler = Hjemler.Utfylt.create(nonEmptyListOf(Hjemmel.SU_PARAGRAF_3)),

        ) shouldBe KunneIkkeLageBrevutkast.FantIkkePerson.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe klage.sakId })
        verify(mocks.klageRepoMock).hentKnyttetVedtaksdato(argThat { it shouldBe klage.id })
        verify(mocks.microsoftGraphApiMock).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `generering av brev feilet`() {

        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val klage = påbegyntVurdertKlage()
        val person = person(fnr = sak.fnr)

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
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
            sakId = sak.id,
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = fritekstTilBrev,
            hjemler = hjemler,

        ) shouldBe KunneIkkeLageBrevutkast.GenereringAvBrevFeilet.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe klage.sakId })
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
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val klage = påbegyntVurdertKlage()
        val person = person(fnr = sak.fnr)
        val pdfAsBytes = "brevbytes".toByteArray()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentKnyttetVedtaksdato(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            sakRepoMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak
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
            sakId = sak.id,
            klageId = klage.id,
            saksbehandler = saksbehandler,
            fritekst = fritekstTilBrev,
            hjemler = hjemler,

        ) shouldBe pdfAsBytes.right()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.sakRepoMock).hentSak(argThat<UUID> { it shouldBe klage.sakId })
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
