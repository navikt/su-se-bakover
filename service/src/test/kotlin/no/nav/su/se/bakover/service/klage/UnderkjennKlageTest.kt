package no.nav.su.se.bakover.service.klage

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
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

internal class UnderkjennKlageTest {

    @Test
    fun `fant ikke klage`() {

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            },
        )

        val klageId = UUID.randomUUID()
        val attestant = NavIdentBruker.Attestant("s2")
        val request = UnderkjennKlageRequest(
            klageId = klageId,
            attestant = attestant,
            grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
            kommentar = "underkjennelseskommentar",
        )
        mocks.service.underkjenn(request) shouldBe KunneIkkeUnderkjenne.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Ugyldig tilstandsovergang fra opprettet`() {
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()
        verifiserUgyldigTilstandsovergang(
            klage = opprettetKlage(sakId = sak.id),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vilkårsvurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVilkårsvurdertKlage(sakId = sak.id, vedtakId = vedtak.id),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vilkårsvurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        verifiserUgyldigTilstandsovergang(
            klage = utfyltVilkårsvurdertKlage(sakId = sak.id, vedtakId = vedtak.id),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vilkårsvurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetVilkårsvurdertKlage(sakId = sak.id, vedtakId = vedtak.id),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra påbegynt vurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        verifiserUgyldigTilstandsovergang(
            klage = påbegyntVurdertKlage(sakId = sak.id, vedtakId = vedtak.id),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra utfylt vurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        verifiserUgyldigTilstandsovergang(
            klage = utfyltVurdertKlage(sakId = sak.id, vedtakId = vedtak.id),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra bekreftet vurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        verifiserUgyldigTilstandsovergang(
            klage = bekreftetVurdertKlage(sakId = sak.id, vedtakId = vedtak.id),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang underkjent vurdering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        verifiserUgyldigTilstandsovergang(
            klage = underkjentKlage(sakId = sak.id, vedtakId = vedtak.id),
        )
    }

    @Test
    fun `Ugyldig tilstandsovergang fra iverksatt`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        verifiserUgyldigTilstandsovergang(
            klage = iverksattKlage(sakId = sak.id, vedtakId = vedtak.id),
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
        val attestant = NavIdentBruker.Attestant("attestant")
        val request = UnderkjennKlageRequest(
            klageId = klage.id,
            attestant = attestant,
            grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
            kommentar = "underkjennelseskommentar",
        )
        mocks.service.underkjenn(request) shouldBe KunneIkkeUnderkjenne.UgyldigTilstand(
            klage::class,
            VurdertKlage.Bekreftet::class,
        ).left()

        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klage.id })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Skal kunne underkjenne klage som er til attestering`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()
        val klage = klageTilAttestering(
            sakId = sak.id,
            vedtakId = vedtak.id,
        )
        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn klage
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            personServiceMock = mock {
                on { hentAktørId(any()) } doReturn AktørId("aktørId").right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("nyOppgaveId").right()
            }
        )

        val klageId = UUID.randomUUID()
        val attestant = NavIdentBruker.Attestant("s2")

        var expectedKlage: VurdertKlage.Bekreftet?
        val request = UnderkjennKlageRequest(
            klageId = klageId,
            attestant = attestant,
            grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
            kommentar = "underkjennelseskommentar",
        )
        mocks.service.underkjenn(request).getOrHandle { throw RuntimeException(it.toString()) }.also {
            expectedKlage = VurdertKlage.Bekreftet.create(
                id = it.id,
                opprettet = fixedTidspunkt,
                sakId = klage.sakId,
                saksnummer = klage.saksnummer,
                fnr = klage.fnr,
                journalpostId = klage.journalpostId,
                oppgaveId = OppgaveId("nyOppgaveId"),
                saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
                vilkårsvurderinger = klage.vilkårsvurderinger,
                vurderinger = klage.vurderinger,
                attesteringer = Attesteringshistorikk.create(
                    listOf(
                        Attestering.Underkjent(
                            attestant = attestant,
                            opprettet = fixedTidspunkt,
                            grunn = request.grunn,
                            kommentar = request.kommentar,
                        ),
                    ),
                ),
                datoKlageMottatt = 1.desember(2021),
            )
            it shouldBe expectedKlage
        }
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        verify(mocks.klageRepoMock).defaultTransactionContext()
        verify(mocks.klageRepoMock).lagre(argThat { it shouldBe expectedKlage }, argThat { it shouldBe TestSessionFactory.transactionContext })
        verify(mocks.oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Klage.Saksbehandler(
                    saksnummer = klage.saksnummer,
                    aktørId = AktørId("aktørId"),
                    journalpostId = klage.journalpostId,
                    tilordnetRessurs = klage.saksbehandler,
                    clock = fixedClock,
                )
            },
        )
        verify(mocks.personServiceMock).hentAktørId(argThat { it shouldBe klage.fnr })
        verify(mocks.oppgaveService).lukkOppgave(klage.oppgaveId)
        mocks.verifyNoMoreInteractions()
    }
}
