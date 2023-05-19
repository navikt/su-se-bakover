package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevRequestForKlage
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avvistKlage
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.utfyltAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
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
        ) shouldBe KunneIkkeLageBrevutkast.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke knyttet vedtak`() {
        val klage = påbegyntVurdertKlage().second
        val person = person(fnr = klage.fnr)
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn null
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Ole Nordmann".right()
            },
        )
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLageBrevutkast.FeilVedBrevRequest(KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvVedtaksbrevDato)
            .left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe klage.fnr })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke saksbehandler`() {
        val klage = påbegyntVurdertKlage().second
        val person = person(fnr = klage.fnr)
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2021)
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn KunneIkkeHenteNavnForNavIdent.FantIkkeBrukerForNavIdent.left()
            },
        )
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")

        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLageBrevutkast.FeilVedBrevRequest(
            KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvSaksbehandlernavn(
                KunneIkkeHenteNavnForNavIdent.FantIkkeBrukerForNavIdent,
            ),
        )
            .left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe klage.fnr })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke person`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val vedtak = sak.vedtakListe.first()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            identClient = mock {
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
        ) shouldBe KunneIkkeLageBrevutkast.FeilVedBrevRequest(KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvPerson(KunneIkkeHentePerson.FantIkkePerson)).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
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
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            identClient = mock {
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

        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Ola Nordmann",
                    fritekst = "",
                    klageDato = 15.januar(2021),
                    vedtaksbrevDato = 1.januar(2021),
                    saksnummer = Saksnummer(12345676),
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
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            identClient = mock {
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

        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
        ) shouldBe pdfAsBytes.right()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Ola Nordmann",
                    fritekst = "",
                    klageDato = 15.januar(2021),
                    vedtaksbrevDato = 1.januar(2021),
                    saksnummer = Saksnummer(12345676),
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne hente brevutkast til klage der klagen har fritekst`() {
        val (sak, klage) = påbegyntVurdertKlage(fritekstTilBrev = "jeg er fritekst for et brev")
        val vedtak = sak.vedtakListe.first()
        val person = person(fnr = sak.fnr)
        val pdfAsBytes = "brevbytes".toByteArray()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            identClient = mock {
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

        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
        ) shouldBe pdfAsBytes.right()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe LagBrevRequest.Klage.Oppretthold(
                    person = person,
                    dagensDato = fixedLocalDate,
                    saksbehandlerNavn = "Ola Nordmann",
                    fritekst = "jeg er fritekst for et brev",
                    klageDato = 15.januar(2021),
                    vedtaksbrevDato = 1.januar(2021),
                    saksnummer = Saksnummer(12345676),
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan ikke lage brev fra opprettet`() {
        skalIkkeKunneHenteBrevUtkastFraTilstand(opprettetKlage())
    }

    @Test
    fun `kan ikke lage brev fra påbegyntVilkårsvurdert`() {
        skalIkkeKunneHenteBrevUtkastFraTilstand(påbegyntVilkårsvurdertKlage())
    }

    @Test
    fun `kan ikke lage brev fra utfyltVilkårsvurdert(TilVurdering)`() {
        skalIkkeKunneHenteBrevUtkastFraTilstand(utfyltVilkårsvurdertKlageTilVurdering())
    }

    @Test
    fun `kan ikke lage brev fra utfyltVilkårsvurdert(Avvist)`() {
        skalIkkeKunneHenteBrevUtkastFraTilstand(utfyltAvvistVilkårsvurdertKlage())
    }

    @Test
    fun `kan ikke lage brev fra bekreftetVilkårsvurdert(TilVurdering)`() {
        skalIkkeKunneHenteBrevUtkastFraTilstand(bekreftetVilkårsvurdertKlageTilVurdering())
    }

    @Test
    fun `kan ikke lage brev fra bekreftetVilkårsvurdert(Avvist)`() {
        skalIkkeKunneHenteBrevUtkastFraTilstand(bekreftetAvvistVilkårsvurdertKlage())
    }

    private fun skalIkkeKunneHenteBrevUtkastFraTilstand(sakOgKlagePair: Pair<Sak, Klage>) {
        val (sak, klage) = sakOgKlagePair
        val vedtak = sak.vedtakListe.first()
        val person = person(fnr = sak.fnr)

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn "Ola Nordmann".right()
            },
            personServiceMock = mock {
                on { hentPerson(any()) } doReturn person.right()
            },
        )

        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = klage.saksbehandler,
        ) shouldBe KunneIkkeLageBrevutkast.FeilVedBrevRequest(KunneIkkeLageBrevRequestForKlage.UgyldigTilstand(klage::class)).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan hente brevutkast fra vurdertKlage`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val person = person(fnr = sak.fnr)

        kanHenteBrevUtkastFraTilstand(
            sak,
            klage,
            person,
            LagBrevRequest.Klage.Oppretthold(
                person = person,
                dagensDato = fixedLocalDate,
                saksbehandlerNavn = "Ola Nordmann",
                fritekst = "",
                klageDato = 15.januar(2021),
                vedtaksbrevDato = 1.januar(2021),
                saksnummer = Saksnummer(12345676),
            ),
        )
    }

    @Test
    fun `kan hente brevutkast fra avvist klage`() {
        val (sak, klage) = avvistKlage()
        val person = person(fnr = sak.fnr)

        kanHenteBrevUtkastFraTilstand(
            sak,
            klage,
            person,
            LagBrevRequest.Klage.Avvist(
                person = person,
                dagensDato = fixedLocalDate,
                saksbehandlerNavn = "Ola Nordmann",
                fritekst = "dette er en fritekst med person opplysninger",
                saksnummer = Saksnummer(12345676),
            ),
        )
    }

    @Test
    fun `kan hente brevutkast fra vurdert klage til attestering`() {
        val (sak, klage) = vurdertKlageTilAttestering()
        val person = person(fnr = sak.fnr)

        kanHenteBrevUtkastFraTilstand(
            sak,
            klage,
            person,
            LagBrevRequest.Klage.Oppretthold(
                person = person,
                dagensDato = fixedLocalDate,
                saksbehandlerNavn = "Ola Nordmann",
                fritekst = "fritekstTilBrev",
                klageDato = 15.januar(2021),
                vedtaksbrevDato = 1.januar(2021),
                saksnummer = Saksnummer(12345676),
            ),
        )
    }

    @Test
    fun `kan hente brevutkast fra avvist til attestering`() {
        val (sak, klage) = avvistKlageTilAttestering()
        val person = person(fnr = sak.fnr)

        kanHenteBrevUtkastFraTilstand(
            sak,
            klage,
            person,
            LagBrevRequest.Klage.Avvist(
                person = person,
                dagensDato = fixedLocalDate,
                saksbehandlerNavn = "Ola Nordmann",
                fritekst = "dette er en fritekst med person opplysninger",
                saksnummer = Saksnummer(12345676),
            ),
        )
    }

    @Test
    fun `kan hente brevutkast fra oversendt klage`() {
        val (sak, klage) = oversendtKlage()
        val person = person(fnr = sak.fnr)

        kanHenteBrevUtkastFraTilstand(
            sak,
            klage,
            person,
            LagBrevRequest.Klage.Oppretthold(
                person = person,
                dagensDato = fixedLocalDate,
                saksbehandlerNavn = "Ola Nordmann",
                fritekst = "fritekstTilBrev",
                klageDato = 15.januar(2021),
                vedtaksbrevDato = 1.januar(2021),
                saksnummer = Saksnummer(12345676),
            ),
        )
    }

    @Test
    fun `kan hente brevutkast fra iverksatt Avvist`() {
        val (sak, klage) = iverksattAvvistKlage()
        val person = person(fnr = sak.fnr)

        kanHenteBrevUtkastFraTilstand(
            sak,
            klage,
            person,
            LagBrevRequest.Klage.Avvist(
                person = person,
                dagensDato = fixedLocalDate,
                saksbehandlerNavn = "Ola Nordmann",
                fritekst = "dette er en fritekst med person opplysninger",
                saksnummer = Saksnummer(12345676),
            ),
        )
    }

    private fun kanHenteBrevUtkastFraTilstand(
        sak: Sak,
        klage: Klage,
        person: Person,
        brevrequestType: LagBrevRequest.Klage,
    ) {
        val vedtak = sak.vedtakListe.first()
        val pdfAsBytes = "brevbytes".toByteArray()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            identClient = mock {
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

        mocks.service.brevutkast(
            klageId = klage.id,
            saksbehandler = saksbehandler,
        ) shouldBe pdfAsBytes.right()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        if (brevrequestType is LagBrevRequest.Klage.Oppretthold) {
            verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        }
        verify(mocks.identClient).hentNavnForNavIdent(argThat { it shouldBe saksbehandler })
        verify(mocks.personServiceMock).hentPerson(argThat { it shouldBe sak.fnr })
        verify(mocks.brevServiceMock).lagBrev(
            argThat {
                it shouldBe when (brevrequestType) {
                    is LagBrevRequest.Klage.Avvist -> LagBrevRequest.Klage.Avvist(
                        person = person,
                        dagensDato = fixedLocalDate,
                        saksbehandlerNavn = "Ola Nordmann",
                        fritekst = brevrequestType.fritekst,
                        saksnummer = Saksnummer(12345676),
                    )
                    is LagBrevRequest.Klage.Oppretthold -> LagBrevRequest.Klage.Oppretthold(
                        person = person,
                        dagensDato = fixedLocalDate,
                        saksbehandlerNavn = "Ola Nordmann",
                        fritekst = brevrequestType.fritekst,
                        klageDato = 15.januar(2021),
                        vedtaksbrevDato = 1.januar(2021),
                        saksnummer = Saksnummer(12345676),
                    )
                }
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
