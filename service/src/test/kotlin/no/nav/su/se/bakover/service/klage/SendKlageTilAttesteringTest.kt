package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeKlageTilAttestering
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avvistKlageTilAttestering
import no.nav.su.se.bakover.test.bekreftetAvvistVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattAvvistKlage
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.oversendtKlage
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.underkjentAvvistKlage
import no.nav.su.se.bakover.test.underkjentKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlageTilVurdering
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import vedtak.domain.Vedtak

internal class SendKlageTilAttesteringTest {

    @Test
    fun `fant ikke klage`() {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )

        val klageId = KlageId.generer()
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")
        mocks.service.sendTilAttestering(
            klageId = klageId,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeSendeKlageTilAttestering.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `skal kunne sende en klage til attestering selv om oppdatering av oppgave feiler`() {
        val klage = bekreftetVurdertKlage().second
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doReturn KunneIkkeOppdatereOppgave.FeilVedHentingAvOppgave.left()
            },
        )

        val saksbehandler = NavIdentBruker.Saksbehandler("s2")
        val actual = mocks.service.sendTilAttestering(klageId = klage.id, saksbehandler = saksbehandler)
        actual.shouldBeRight()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).lagre(argThat { it.right() shouldBe actual }, anyOrNull())
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.oppgaveService).oppdaterOppgave(
            argThat { it shouldBe OppgaveId("oppgaveIdKlage") },
            argThat {
                it shouldBe OppdaterOppgaveInfo(
                    beskrivelse = "Sendt klagen til attestering",
                    oppgavetype = Oppgavetype.ATTESTERING,
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs,
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `en klageTilAttestering(Vurdert) er en åpen klage`() {
        val klage = vurdertKlageTilAttestering().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `en klageTilAttestering(avvist) er en åpen klage`() {
        val klage = avvistKlageTilAttestering().second
        klage.erÅpen() shouldBe true
    }

    @Test
    fun `Ugyldig tilstandsovergang fra opprettet`() {
        verifiserUgyldigTilstandsovergang(
            klage = opprettetKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vilkårsvurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVilkårsvurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering til vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = utfyltVilkårsvurdertKlageTilVurdering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering til vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetVilkårsvurdertKlageTilVurdering().second,
        )
    }

    @Test
    fun `ugyldig statusovergang fra bekreftet avvist vilkårsvurdert klage til attestering`() {
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetAvvistVilkårsvurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = utfyltVurdertKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra til attestering`() {
        verifiserUgyldigTilstandsovergang(
            klage = vurdertKlageTilAttestering().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt`() {
        verifiserUgyldigTilstandsovergang(
            klage = oversendtKlage().second,
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra avvist`() {
        verifiserUgyldigTilstandsovergang(
            klage = iverksattAvvistKlage().second,
        )
    }

    private fun verifiserUgyldigTilstandsovergang(
        klage: Klage,
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
            },
        )
        mocks.service.sendTilAttestering(
            klageId = klage.id,
            saksbehandler = klage.saksbehandler,
        ) shouldBe KunneIkkeSendeKlageTilAttestering.UgyldigTilstand(klage::class).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne sende en bekreftet vurdert klage til attestering`() {
        bekreftetVurdertKlage().let {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs,
            )
        }
    }

    @Test
    fun `Skal kunne sende en underkjent til vurdering klage til attestering`() {
        underkjentKlageTilVurdering().let {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                tilordnetRessurs = it.second.attesteringer.let { attesteringshistorikk ->
                    require(attesteringshistorikk.size == 1)
                    OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(attesteringshistorikk.first().attestant.navIdent)
                },
            )
        }
    }

    @Test
    fun `skal kunne sende en underkjent avvist klage til attestering`() {
        underkjentAvvistKlage().let {
            verifiserGyldigStatusovergang(
                vedtak = it.first.vedtakListe.first(),
                klage = it.second,
                tilordnetRessurs = it.second.attesteringer.let { attesteringshistorikk ->
                    require(attesteringshistorikk.size == 1)
                    OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(attesteringshistorikk.first().attestant.navIdent)
                },
            )
        }
    }

    private fun verifiserGyldigStatusovergang(
        vedtak: Vedtak,
        klage: Klage,
        tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
    ) {
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            vedtakServiceMock = mock {
                on { hentForVedtakId(any()) } doReturn vedtak
            },
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
        )

        val saksbehandlerSomSendteTilAttestering = NavIdentBruker.Saksbehandler("saksbehandlerSomSendteTilAttestering")
        var expectedKlage: KlageTilAttestering?
        mocks.service.sendTilAttestering(
            klageId = klage.id,
            saksbehandler = saksbehandlerSomSendteTilAttestering,
        ).getOrFail().also {
            expectedKlage = if (klage is AvvistKlage) {
                KlageTilAttestering.Avvist(
                    forrigeSteg = klage,
                    saksbehandler = saksbehandlerSomSendteTilAttestering,
                    sakstype = klage.sakstype,
                )
            } else {
                KlageTilAttestering.Vurdert(
                    forrigeSteg = klage as VurdertKlage.Bekreftet,
                    saksbehandler = saksbehandlerSomSendteTilAttestering,
                    sakstype = klage.sakstype,
                )
            }
            it shouldBe expectedKlage!!
        }

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        verify(mocks.klageRepoMock).lagre(
            argThat { it shouldBe expectedKlage },
            argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.oppgaveService).oppdaterOppgave(
            argThat { it shouldBe OppgaveId("oppgaveIdKlage") },
            argThat {
                it shouldBe OppdaterOppgaveInfo(
                    beskrivelse = "Sendt klagen til attestering",
                    oppgavetype = Oppgavetype.ATTESTERING,
                    tilordnetRessurs = tilordnetRessurs,
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
