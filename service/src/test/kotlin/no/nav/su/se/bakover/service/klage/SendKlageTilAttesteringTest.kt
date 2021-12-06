package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.bekreftetVilkårsvurdertKlage
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.iverksattKlage
import no.nav.su.se.bakover.test.klageTilAttestering
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.påbegyntVilkårsvurdertKlage
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
import no.nav.su.se.bakover.test.underkjentKlage
import no.nav.su.se.bakover.test.utfyltVilkårsvurdertKlage
import no.nav.su.se.bakover.test.utfyltVurdertKlage
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class SendKlageTilAttesteringTest {

    @Test
    fun `fant ikke klage`() {

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )

        val klageId = UUID.randomUUID()
        val saksbehandler = NavIdentBruker.Saksbehandler("s2")
        mocks.service.sendTilAttestering(
            klageId = klageId,
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeSendeTilAttestering.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig tilstandsovergang fra opprettet`() {
        verifiserUgyldigTilstandsovergang(
            klage = opprettetKlage(),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vilkårsvurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVilkårsvurdertKlage(),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = utfyltVilkårsvurdertKlage(),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetVilkårsvurdertKlage(),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVurdertKlage(),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vurdering`() {
        verifiserUgyldigTilstandsovergang(
            klage = utfyltVurdertKlage(),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra til attestering`() {
        verifiserUgyldigTilstandsovergang(
            klage = klageTilAttestering(),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt`() {
        verifiserUgyldigTilstandsovergang(
            klage = iverksattKlage(),
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
        ) shouldBe KunneIkkeSendeTilAttestering.UgyldigTilstand(klage::class, KlageTilAttestering::class).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne bekrefte bekreftet vurdert klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val bekreftetVurdertKlage = bekreftetVurdertKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergang(
            vedtak = vedtak,
            klage = bekreftetVurdertKlage,
        )
    }

    @Test
    fun `Skal kunne bekrefte underkjent klage`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val underkjentKlage = underkjentKlage(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        verifiserGyldigStatusovergang(
            vedtak = vedtak,
            klage = underkjentKlage,
            attesteringer = underkjentKlage.attesteringer,
            tilordnetRessurs = underkjentKlage.attesteringer.let {
                assert(it.size == 1)
                it.first().attestant
            },
        )
    }

    private fun verifiserGyldigStatusovergang(
        vedtak: Vedtak,
        klage: VurdertKlage.Bekreftet,
        attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
        tilordnetRessurs: NavIdentBruker.Attestant? = null,
    ) {
        val session = TestSessionFactory()

        session.withTransactionContext { transactionContext ->

            val mocks = KlageServiceMocks(
                klageRepoMock = mock {
                    on { hentKlage(any()) } doReturn klage
                    on { defaultTransactionContext() } doReturn transactionContext
                },
                vedtakRepoMock = mock {
                    on { hentForVedtakId(any()) } doReturn vedtak
                },
                personServiceMock = mock {
                    on { hentAktørId(any()) } doReturn AktørId("aktørId").right()
                },
                oppgaveService = mock {
                    on { opprettOppgave(any()) } doReturn OppgaveId("nyOppgaveId").right()
                },
            )

            var expectedKlage: KlageTilAttestering?
            mocks.service.sendTilAttestering(
                klageId = klage.id,
                saksbehandler = NavIdentBruker.Saksbehandler("bekreftetVilkårsvurderingene"),
            ).orNull()!!.also {
                expectedKlage = KlageTilAttestering.create(
                    id = it.id,
                    opprettet = fixedTidspunkt,
                    sakId = klage.sakId,
                    saksnummer = klage.saksnummer,
                    fnr = klage.fnr,
                    journalpostId = klage.journalpostId,
                    oppgaveId = OppgaveId("nyOppgaveId"),
                    saksbehandler = NavIdentBruker.Saksbehandler("bekreftetVilkårsvurderingene"),
                    vilkårsvurderinger = klage.vilkårsvurderinger,
                    vurderinger = klage.vurderinger,
                    attesteringer = attesteringer,
                    datoKlageMottatt = 1.desember(2021),
                )
                it shouldBe expectedKlage
            }

            verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
            verify(mocks.klageRepoMock).lagre(
                argThat {
                    it shouldBe expectedKlage
                },
                argThat { it shouldBe transactionContext },
            )
            verify(mocks.klageRepoMock).defaultTransactionContext()
            verify(mocks.oppgaveService).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Klage.Saksbehandler(
                        saksnummer = klage.saksnummer,
                        aktørId = AktørId("aktørId"),
                        journalpostId = klage.journalpostId,
                        tilordnetRessurs = tilordnetRessurs,
                        clock = fixedClock,
                    )
                },
            )
            verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe klage.fnr })
            verify(mocks.oppgaveService).lukkOppgave(klage.oppgaveId)
            mocks.verifyNoMoreInteractions()
        }
    }
}
