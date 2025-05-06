package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import dokument.domain.KunneIkkeLageDokument
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevKommandoForKlage
import no.nav.su.se.bakover.domain.klage.brev.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.avvistKlage
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonAnnet
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.utfyltAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.ZoneOffset
import java.util.UUID

internal class HentBrevutkastTest {

    @Test
    fun `fant ikke klage`() {
        val (sak, _) = påbegyntVurdertKlage()
        val mocks = KlageServiceMocks(
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        )
        val klageId = KlageId.generer()
        mocks.service.brevutkast(
            sakId = sak.id,
            klageId = klageId,
            ident = saksbehandler,
        ) shouldBe KunneIkkeLageBrevutkast.FantIkkeKlage.left()
        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke knyttet vedtak`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn null
            },
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            dokumentHendelseRepo = mock {
                on { hentVedtaksbrevdatoForSakOgVedtakId(any(), any(), anyOrNull()) } doReturn null
            },
        )

        mocks.service.brevutkast(
            sakId = sak.id,
            klageId = klage.id,
            ident = saksbehandler,
        ) shouldBe KunneIkkeLageBrevutkast.FeilVedBrevRequest(KunneIkkeLageBrevKommandoForKlage.FeilVedHentingAvVedtaksbrevDato)
            .left()

        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.dokumentHendelseRepo).hentVedtaksbrevdatoForSakOgVedtakId(any(), any(), anyOrNull())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke saksbehandler`() {
        val (sak, klage) = påbegyntVurdertKlage()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2021)
            },
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left()
            },
        )
        mocks.service.brevutkast(
            sakId = sak.id,
            klageId = klage.id,
            ident = saksbehandler,
        ) shouldBe KunneIkkeLageBrevutkast.KunneIkkeGenererePdf(
            KunneIkkeLageDokument.FeilVedHentingAvInformasjon,
        ).left()

        verify(mocks.brevServiceMock).lagDokument(any(), anyOrNull())
        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke person`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val vedtak = sak.vedtakListe.first()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left()
            },
        )

        mocks.service.brevutkast(
            sakId = sak.id,
            klageId = klage.id,
            ident = saksbehandler,
        ) shouldBe KunneIkkeLageBrevutkast.KunneIkkeGenererePdf(
            KunneIkkeLageDokument.FeilVedHentingAvInformasjon,
        ).left()

        verify(mocks.brevServiceMock).lagDokument(any(), anyOrNull())
        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `generering av brev feilet`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val vedtak = sak.vedtakListe.first()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
            },
        )

        mocks.service.brevutkast(
            sakId = sak.id,
            klageId = klage.id,
            ident = saksbehandler,
        ) shouldBe KunneIkkeLageBrevutkast.KunneIkkeGenererePdf(KunneIkkeLageDokument.FeilVedGenereringAvPdf).left()

        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.brevServiceMock).lagDokument(
            argThat {
                it shouldBe KlageDokumentCommand.Oppretthold(
                    fødselsnummer = sak.fnr,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                    attestant = null,
                    fritekst = "",
                    klageDato = 15.januar(2021),
                    vedtaksbrevDato = 1.januar(2021),
                    saksnummer = Saksnummer(12345676),
                )
            },
            anyOrNull(),
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne hente brevutkast til klage`() {
        val (sak, klage) = påbegyntVurdertKlage()
        val vedtak = sak.vedtakListe.first()
        person(fnr = sak.fnr)
        val pdfAsBytes = PdfA("brevbytes".toByteArray())

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataInformasjonAnnet(
                    pdf = pdfAsBytes,
                    tittel = "test-dokument-informasjon-annet",
                ).right()
            },
        )

        mocks.service.brevutkast(sakId = sak.id, klageId = klage.id, ident = saksbehandler) shouldBe pdfAsBytes.right()

        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.brevServiceMock).lagDokument(
            argThat {
                it shouldBe KlageDokumentCommand.Oppretthold(
                    fritekst = "",
                    klageDato = 15.januar(2021),
                    vedtaksbrevDato = 1.januar(2021),
                    saksnummer = Saksnummer(12345676),
                    fødselsnummer = sak.fnr,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                    attestant = null,
                )
            },
            anyOrNull(),
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne hente brevutkast til klage der klagen har fritekst`() {
        val (sak, klage) = påbegyntVurdertKlage(fritekstTilBrev = "jeg er fritekst for et brev")
        val vedtak = sak.vedtakListe.first()
        val pdfAsBytes = PdfA("brevbytes".toByteArray())

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataInformasjonAnnet(
                    pdf = pdfAsBytes,
                    tittel = "test-dokument-informasjon-annet",
                ).right()
            },
        )

        mocks.service.brevutkast(sakId = sak.id, klageId = klage.id, ident = saksbehandler) shouldBe pdfAsBytes.right()

        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.brevServiceMock).lagDokument(
            argThat {
                it shouldBe KlageDokumentCommand.Oppretthold(
                    fødselsnummer = sak.fnr,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                    attestant = null,
                    fritekst = "jeg er fritekst for et brev",
                    klageDato = 15.januar(2021),
                    vedtaksbrevDato = 1.januar(2021),
                    saksnummer = Saksnummer(12345676),
                )
            },
            anyOrNull(),
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

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },

        )

        mocks.service.brevutkast(
            sakId = sak.id,
            klageId = klage.id,
            ident = saksbehandler,
        ) shouldBe KunneIkkeLageBrevutkast.FeilVedBrevRequest(KunneIkkeLageBrevKommandoForKlage.UgyldigTilstand(klage::class))
            .left()

        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan hente brevutkast fra vurdertKlage`() {
        val (sak, klage) = påbegyntVurdertKlage()

        assertAndVerifyBrevutkast(
            sak = sak,
            klage = klage,
            KlageDokumentCommand.Oppretthold(
                fødselsnummer = sak.fnr,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                attestant = null,
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

        assertAndVerifyBrevutkast(
            sak = sak,
            klage = klage,
            brevRequest = KlageDokumentCommand.Avvist(
                fødselsnummer = sak.fnr,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                attestant = null,
                fritekst = "dette er en fritekst med person opplysninger",
                saksnummer = Saksnummer(12345676),
            ),
        )
    }

    @Test
    fun `kan hente brevutkast fra vurdert klage til attestering`() {
        val (sak, klage) = vurdertKlageTilAttestering()

        assertAndVerifyBrevutkast(
            sak = sak,
            klage = klage,

            brevRequest = KlageDokumentCommand.Oppretthold(
                fødselsnummer = sak.fnr,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                attestant = null,
                fritekst = "fritekstTilBrev",
                klageDato = 15.januar(2021),
                vedtaksbrevDato = 1.januar(2021),
                saksnummer = Saksnummer(12345676),
            ),
            utførtAv = attestant,
            expectedIdentClientCalls = 2,
        )
    }

    @Test
    fun `kan hente brevutkast fra avvist til attestering`() {
        val (sak, klage) = avvistKlageTilAttestering()

        assertAndVerifyBrevutkast(
            sak = sak,
            klage = klage,
            brevRequest = KlageDokumentCommand.Avvist(
                fødselsnummer = sak.fnr,
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                attestant = null,
                fritekst = "dette er en fritekst med person opplysninger",
                saksnummer = Saksnummer(12345676),
            ),
            utførtAv = attestant,
            expectedIdentClientCalls = 2,
        )
    }

    @Test
    fun `kan hente brevutkast fra oversendt klage`() {
        skalIkkeKunneHenteBrevUtkastFraTilstand(oversendtKlage())
    }

    @Test
    fun `kan hente brevutkast fra iverksatt Avvist`() {
        skalIkkeKunneHenteBrevUtkastFraTilstand(iverksattAvvistKlage())
    }

    private fun assertAndVerifyBrevutkast(
        sak: Sak,
        klage: Klage,
        brevRequest: KlageDokumentCommand,
        utførtAv: NavIdentBruker = saksbehandler,
        expectedIdentClientCalls: Int = 1,
    ) {
        val vedtak = sak.vedtakListe.first()
        val pdfAsBytes = PdfA("brevbytes".toByteArray())

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn vedtak.opprettet.toLocalDate(ZoneOffset.UTC)
            },
            sakServiceMock = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },

            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataInformasjonAnnet(
                    pdf = pdfAsBytes,
                    tittel = "test-dokument-informasjon-annet",
                ).right()
            },
        )

        mocks.service.brevutkast(sakId = sak.id, klageId = klage.id, ident = utførtAv) shouldBe pdfAsBytes.right()

        verify(mocks.sakServiceMock).hentSak(argThat<UUID> { it shouldBe sak.id })
        if (brevRequest is KlageDokumentCommand.Oppretthold) {
            verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        }
        verify(mocks.brevServiceMock).lagDokument(
            argThat {
                it shouldBe when (brevRequest) {
                    is KlageDokumentCommand.Avvist -> KlageDokumentCommand.Avvist(
                        fødselsnummer = sak.fnr,
                        saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                        attestant = if (expectedIdentClientCalls == 2) NavIdentBruker.Attestant("attestant") else null,
                        fritekst = brevRequest.fritekst,
                        saksnummer = Saksnummer(12345676),
                    )

                    is KlageDokumentCommand.Oppretthold -> KlageDokumentCommand.Oppretthold(
                        fødselsnummer = sak.fnr,
                        saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                        attestant = if (expectedIdentClientCalls == 2) NavIdentBruker.Attestant("attestant") else null,
                        fritekst = brevRequest.fritekst,
                        klageDato = 15.januar(2021),
                        vedtaksbrevDato = 1.januar(2021),
                        saksnummer = Saksnummer(12345676),
                    )
                }
            },
            anyOrNull(),
        )
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }
}
