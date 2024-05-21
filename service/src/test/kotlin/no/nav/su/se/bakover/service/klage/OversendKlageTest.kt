package no.nav.su.se.bakover.service.klage

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.Klageinstanshendelser
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevKommandoForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeTilKlageinstans
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonAnnet
import no.nav.su.se.bakover.test.dokumentUtenMetadataVedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.underkjentAvvistKlage
import no.nav.su.se.bakover.test.underkjentKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.util.UUID

internal class OversendKlageTest {

    @Test
    fun `fant ikke klage`() {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )
        val klageId = KlageId.generer()
        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.oversend(
            klageId = klageId,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `en oversendtKlage er ikke en åpen klage`() {
        val klage = oversendtKlage().second
        klage.erÅpen() shouldBe false
    }

    @Test
    fun `Attestant og saksbehandler kan ikke være samme person`() {
        val klage = vurdertKlageTilAttestering().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2021)
            },
        )
        val attestant = NavIdentBruker.Attestant(klage.saksbehandler.navIdent)
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kunne ikke lage brevrequest`() {
        val klage = vurdertKlageTilAttestering().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn null
            },
        )
        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.KunneIkkeLageBrevRequest(
            KunneIkkeLageBrevKommandoForKlage.FeilVedHentingAvVedtaksbrevDato,
        ).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(any())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Dokumentgenerering feilet`() {
        val (sak, klage) = vurdertKlageTilAttestering()
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2021)
            },

            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
            },
        )
        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.KunneIkkeLageDokument(
            KunneIkkeLageDokument.FeilVedGenereringAvPdf,
        ).left()

        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })

        verify(mocks.brevServiceMock).lagDokument(
            argThat {
                it shouldBe KlageDokumentCommand.Oppretthold(
                    fødselsnummer = sak.fnr,
                    saksbehandler = klage.saksbehandler,
                    attestant = attestant,
                    fritekst = klage.vurderinger.fritekstTilOversendelsesbrev,
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
    fun `Fant ikke journalpost-id knyttet til vedtaket`() {
        val (sak, klage) = vurdertKlageTilAttestering()

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2021)
            },

            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataVedtak(pdf = PdfA("brevbytes".toByteArray())).right()
            },
            vedtakServiceMock = mock {
                on { hentJournalpostId(any()) } doReturn null
            },
        )
        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.FantIkkeJournalpostIdKnyttetTilVedtaket.left()

        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.brevServiceMock).lagDokument(
            argThat {
                it shouldBe KlageDokumentCommand.Oppretthold(
                    fødselsnummer = sak.fnr,
                    saksbehandler = klage.saksbehandler,
                    attestant = attestant,
                    fritekst = klage.vurderinger.fritekstTilOversendelsesbrev,
                    klageDato = 15.januar(2021),
                    vedtaksbrevDato = 1.januar(2021),
                    saksnummer = Saksnummer(12345676),
                )
            },
            anyOrNull(),
        )
        verify(mocks.vedtakServiceMock).hentJournalpostId(argThat { it shouldBe klage.vilkårsvurderinger.vedtakId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Kunne ikke oversende til klageinstans (client feil)`() {
        val (sak, klage) = vurdertKlageTilAttestering()
        val journalpostIdKnyttetTilVedtakDetKlagePå = JournalpostId("journalpostIdKnyttetTilVedtakDetKlagePå")
        val pdf = PdfA("brevbytes".toByteArray())
        val dokumentUtenMetadataVedtak = dokumentUtenMetadataInformasjonAnnet(
            pdf = pdf,
            tittel = "test-dokument-informasjon-annet",
        )
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2021)
            },
            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataVedtak.right()
            },
            vedtakServiceMock = mock {
                on { hentJournalpostId(any()) } doReturn journalpostIdKnyttetTilVedtakDetKlagePå
            },
            klageClient = mock {
                on { sendTilKlageinstans(any(), any()) } doReturn KunneIkkeOversendeTilKlageinstans.left()
            },
        )
        val attestant = NavIdentBruker.Attestant("s2")
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeOversendeKlage.KunneIkkeOversendeTilKlageinstans.left()

        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.brevServiceMock).lagDokument(
            argThat {
                it shouldBe KlageDokumentCommand.Oppretthold(
                    fødselsnummer = sak.fnr,
                    saksbehandler = klage.saksbehandler,
                    attestant = attestant,
                    fritekst = klage.vurderinger.fritekstTilOversendelsesbrev,
                    klageDato = 15.januar(2021),
                    vedtaksbrevDato = 1.januar(2021),
                    saksnummer = sak.saksnummer,
                )
            },
            anyOrNull(),
        )
        verify(mocks.vedtakServiceMock).hentJournalpostId(argThat { it shouldBe klage.vilkårsvurderinger.vedtakId })
        val expectedKlage = OversendtKlage(
            forrigeSteg = klage,
            attesteringer = Attesteringshistorikk.create(
                Attestering.Iverksatt(attestant = attestant, opprettet = fixedTidspunkt),
            ),
            klageinstanshendelser = Klageinstanshendelser.empty(),
        )
        verify(mocks.klageClient).sendTilKlageinstans(
            klage = argThat { it shouldBe expectedKlage },
            journalpostIdForVedtak = argThat { it shouldBe journalpostIdKnyttetTilVedtakDetKlagePå },
        )
        verify(mocks.klageRepoMock).lagre(eq(expectedKlage), anyOrNull())
        verify(mocks.brevServiceMock).lagreDokument(
            argThat {
                it shouldBe Dokument.MedMetadata.Informasjon.Annet(
                    utenMetadata = dokumentUtenMetadataVedtak,
                    metadata = Dokument.Metadata(
                        sakId = sak.id,
                        søknadId = null,
                        vedtakId = null,
                        revurderingId = null,
                        klageId = klage.id.value,
                        journalpostId = null,
                        brevbestillingId = null,
                    ),
                )
            },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig tilstandsovergang fra opprettet`() {
        opprettetKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vilkårsvurdering`() {
        påbegyntVilkårsvurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering`() {
        utfyltVilkårsvurdertKlageTilVurdering().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering avvist`() {
        utfyltAvvistVilkårsvurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering til vurdering`() {
        bekreftetVilkårsvurdertKlageTilVurdering().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering avvist`() {
        bekreftetAvvistVilkårsvurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vurdering`() {
        påbegyntVurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vurdering`() {
        utfyltVurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vurdering`() {
        bekreftetVurdertKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang underkjent vurdering`() {
        underkjentKlageTilVurdering().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang underkjent vurdering avvist`() {
        underkjentAvvistKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt`() {
        oversendtKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    @Test
    fun `Ugyldig tilstandsovergang fra avvist`() {
        iverksattAvvistKlage().also {
            verifiserUgyldigTilstandsovergang(
                klage = it.second,
            )
        }
    }

    private fun verifiserUgyldigTilstandsovergang(klage: Klage) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
        )
        mocks.service.oversend(
            klageId = klage.id,
            attestant = NavIdentBruker.Attestant("attestant"),
        ) shouldBe KunneIkkeOversendeKlage.UgyldigTilstand(klage::class).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne oversende en klage som er til attestering`() {
        val (sak, klage) = vurdertKlageTilAttestering()
        val journalpostIdForVedtak = JournalpostId(UUID.randomUUID().toString())
        val observerMock: StatistikkEventObserver = mock { on { handle(any()) }.then {} }
        val pdf = PdfA("brevbytes".toByteArray())
        val dokumentUtenMetadata = dokumentUtenMetadataInformasjonAnnet(
            pdf = pdf,
            tittel = "test-dokument-informasjon-annet",
        )
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2021)
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            vedtakServiceMock = mock {
                on { hentJournalpostId(any()) } doReturn journalpostIdForVedtak
            },

            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadata.right()
            },
            klageClient = mock {
                on { sendTilKlageinstans(any(), any()) } doReturn Unit.right()
            },
            oppgaveService = mock {
                on { lukkOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            observer = observerMock,
        )
        val attestant = NavIdentBruker.Attestant("attestant")

        var expectedKlage: OversendtKlage?
        mocks.service.oversend(
            klageId = klage.id,
            attestant = attestant,
        ).getOrElse { fail(it.toString()) }.also {
            expectedKlage = OversendtKlage(
                forrigeSteg = klage,
                attesteringer = Attesteringshistorikk.create(
                    Attestering.Iverksatt(attestant = attestant, opprettet = fixedTidspunkt),
                ),
                klageinstanshendelser = Klageinstanshendelser.empty(),
            )
            it shouldBe expectedKlage
            verify(observerMock).handle(argThat { actual -> StatistikkEvent.Behandling.Klage.Oversendt(it) shouldBe actual })
        }

        verify(mocks.klageRepoMock).hentVedtaksbrevDatoSomDetKlagesPå(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.brevServiceMock).lagDokument(
            argThat {
                it shouldBe KlageDokumentCommand.Oppretthold(
                    fødselsnummer = sak.fnr,
                    saksbehandler = klage.saksbehandler,
                    attestant = attestant,
                    fritekst = klage.vurderinger.fritekstTilOversendelsesbrev,
                    klageDato = 15.januar(2021),
                    vedtaksbrevDato = 1.januar(2021),
                    saksnummer = klage.saksnummer,
                )
            },
            anyOrNull(),
        )
        verify(mocks.vedtakServiceMock).hentJournalpostId(argThat { it shouldBe klage.vilkårsvurderinger.vedtakId })
        verify(mocks.klageClient).sendTilKlageinstans(
            klage = argThat { it shouldBe expectedKlage },
            journalpostIdForVedtak = argThat { it shouldBe journalpostIdForVedtak },
        )
        verify(mocks.brevServiceMock).lagreDokument(
            argThat {
                it shouldBe Dokument.MedMetadata.Informasjon.Annet(
                    utenMetadata = dokumentUtenMetadata,
                    metadata = Dokument.Metadata(
                        sakId = sak.id,
                        søknadId = null,
                        vedtakId = null,
                        revurderingId = null,
                        klageId = klage.id.value,
                        journalpostId = null,
                        brevbestillingId = null,
                    ),
                )
            },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(mocks.klageRepoMock).lagre(
            argThat { it shouldBe expectedKlage },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(mocks.oppgaveService).lukkOppgave(argThat { it shouldBe klage.oppgaveId }, argThat { it shouldBe OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(attestant.navIdent) })
        mocks.verifyNoMoreInteractions()
    }
}
