package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import dokument.domain.Dokument
import dokument.domain.Dokumenttilstand
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.dokumentUtenMetadataVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
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
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class IverksettAvvistKlageTest {

    @Test
    fun `fant ikke klage`() {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )

        val klageId = KlageId.generer()
        val attestant = NavIdentBruker.Attestant("attestantensen")

        mocks.service.iverksettAvvistKlage(
            klageId,
            attestant,
        ) shouldBe KunneIkkeIverksetteAvvistKlage.FantIkkeKlage.left()
        Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Attestant og saksbehandler kan ikke være samme person`() {
        val klage = avvistKlageTilAttestering().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2021)
            },
        )
        val attestant = NavIdentBruker.Attestant(klage.saksbehandler.navIdent)
        mocks.service.iverksettAvvistKlage(
            klageId = klage.id,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteAvvistKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `er ikke åpen`() {
        val klage = iverksattAvvistKlage().second
        klage.erÅpen() shouldBe false
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
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering til vurdering`() {
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
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering`() {
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
    fun `Ugyldig tilstandsovergang underkjent vurdering til vurdering`() {
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
    fun `Ugyldig tilstandsovergang fra oversendt`() {
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
        mocks.service.iverksettAvvistKlage(
            klageId = klage.id,
            attestant = NavIdentBruker.Attestant("attestant"),
        ) shouldBe KunneIkkeIverksetteAvvistKlage.UgyldigTilstand(klage::class).left()

        Mockito.verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan iverksette en klage som er til attestering avvist`() {
        val (_, klage) = avvistKlageTilAttestering(fritekstTilBrev = "dette er min fritekst")
        val attestant = NavIdentBruker.Attestant("attestant")
        val pdfA = PdfA("myDoc".toByteArray())
        val observerMock: StatistikkEventObserver = mock { on { handle(any()) }.then {} }
        val dokumentUtenMetadataVedtak = dokumentUtenMetadataVedtak(pdf = pdfA)
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { hentVedtaksbrevDatoSomDetKlagesPå(any()) } doReturn 1.januar(2022)
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            brevServiceMock = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataVedtak.right()
            },
            oppgaveService = mock {
                on { lukkOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            vedtakServiceMock = mock {
                doNothing().whenever(it).lagre(any())
            },
            observer = observerMock,
        )

        val actual = mocks.service.iverksettAvvistKlage(klage.id, attestant).getOrFail()

        val expected = IverksattAvvistKlage(
            forrigeSteg = klage,
            attesteringer = Attesteringshistorikk.create(
                listOf(
                    Attestering.Iverksatt(
                        attestant = attestant,
                        opprettet = fixedTidspunkt,
                    ),
                ),
            ),
        )

        actual shouldBe expected

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.brevServiceMock).lagDokument(
            argThat {
                it shouldBe KlageDokumentCommand.Avvist(
                    fødselsnummer = klage.fnr,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                    attestant = NavIdentBruker.Attestant("attestant"),
                    fritekst = "dette er min fritekst",
                    saksnummer = klage.saksnummer,
                )
            },
            anyOrNull(),
        )
        var expectedVedtak: Klagevedtak.Avvist? = null
        verify(mocks.vedtakServiceMock).lagreITransaksjon(
            vedtak = argThat {
                expectedVedtak = Klagevedtak.Avvist(
                    id = it.id,
                    opprettet = fixedTidspunkt,
                    saksbehandler = expected.saksbehandler,
                    attestant = expected.attesteringer.first().attestant,
                    behandling = expected,
                    dokumenttilstand = Dokumenttilstand.GENERERT,
                )
                it shouldBe expectedVedtak!!
            },
            tx = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(mocks.brevServiceMock).lagreDokument(
            argThat {
                it shouldBe Dokument.MedMetadata.Vedtak(
                    utenMetadata = dokumentUtenMetadataVedtak,
                    metadata = Dokument.Metadata(
                        sakId = klage.sakId,
                        klageId = klage.id.value,
                        vedtakId = expectedVedtak!!.id,
                    ),
                    distribueringsadresse = null,
                )
            },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(mocks.klageRepoMock).lagre(
            argThat { it shouldBe expected },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(mocks.oppgaveService).lukkOppgave(argThat { it shouldBe expected.oppgaveId }, argThat { it shouldBe OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(attestant.navIdent) })
        verify(observerMock).handle(
            argThat {
                it shouldBe StatistikkEvent.Behandling.Klage.Avvist(
                    Klagevedtak.Avvist.fromIverksattAvvistKlage(
                        iverksattAvvistKlage = actual,
                        clock = fixedClock,
                    ).copy(
                        id = (it as StatistikkEvent.Behandling.Klage.Avvist).vedtak.id,
                    ),
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
