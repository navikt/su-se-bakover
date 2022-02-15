package no.nav.su.se.bakover.service.sak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class SakServiceImplTest {

    @Test
    fun `Oppretter sak og publiserer event`() {
        val sakId = UUID.randomUUID()
        val sak: Sak = mock {
            on { id } doReturn sakId
        }
        val sakRepo: SakRepo = mock {
            on { hentSak(any<UUID>()) } doReturn sak
        }

        val observer: EventObserver = mock()

        val sakService = SakServiceImpl(sakRepo, fixedClock)
        sakService.observers.add(observer)
        sakService.opprettSak(mock { on { id } doReturn sakId })

        verify(sakRepo).opprettSak(any())
        verify(sakRepo).hentSak(sak.id)
        verify(observer).handle(argThat { it shouldBe Event.Statistikk.SakOpprettet(sak) })
    }

    @Test
    fun `Publiserer ikke event ved feil av opprettelse av sak`() {
        val sakRepo: SakRepo = mock {
            on { opprettSak(any()) } doThrow RuntimeException("hehe exception")
        }

        val observer: EventObserver = mock()

        val sakService = SakServiceImpl(sakRepo, fixedClock)
        sakService.observers.add(observer)
        assertThrows<RuntimeException> {
            sakService.opprettSak(mock())
            verify(sakRepo).opprettSak(any())
            verifyNoMoreInteractions(sakRepo)
            verifyNoInteractions(observer)
        }
    }

    @Test
    fun `henter bare åpen søknad på en sak`() {
        val nySakMedjournalførtSøknadOgOppgave = nySakMedjournalførtSøknadOgOppgave().second
        val sakRepo: SakRepo = mock {
            on { hentÅpneBehandlinger() } doReturn listOf(
                Behandlingsoversikt(
                    saksnummer = Saksnummer(nummer = 2021),
                    behandlingsId = nySakMedjournalførtSøknadOgOppgave.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingsoversikt.Behandlingsstatus.NY_SØKNAD,
                    behandlingStartet = nySakMedjournalførtSøknadOgOppgave.opprettet,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo, fixedClock)
        val sakMedÅpenSøknad = sakService.hentÅpneBehandlingerForAlleSaker()

        sakMedÅpenSøknad shouldBe listOf(
            Behandlingsoversikt(
                saksnummer = Saksnummer(nummer = 2021),
                behandlingsId = nySakMedjournalførtSøknadOgOppgave.id,
                behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                status = Behandlingsoversikt.Behandlingsstatus.NY_SØKNAD,
                behandlingStartet = nySakMedjournalførtSøknadOgOppgave.opprettet,
            ),
        )
    }

    @Test
    fun `henter bare åpne søknadsbehandlinger på en sak`() {
        val saksnr1 = Saksnummer(2021)
        val saksnr2 = Saksnummer(2022)

        val uavklartSøkandsbehandling = søknadsbehandlingVilkårsvurdertUavklart(saksnr1).second
        val underkjentSøknadsbehandling = søknadsbehandlingUnderkjentInnvilget(saksnr1).second
        val tilAttesteringSøknadsbehandling = søknadsbehandlingTilAttesteringInnvilget(saksnr2).second

        val sakRepo: SakRepo = mock {
            on { hentÅpneBehandlinger() } doReturn listOf(
                Behandlingsoversikt(
                    saksnummer = saksnr1,
                    behandlingsId = uavklartSøkandsbehandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = uavklartSøkandsbehandling.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = saksnr1,
                    behandlingsId = underkjentSøknadsbehandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDERKJENT,
                    behandlingStartet = underkjentSøknadsbehandling.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = saksnr2,
                    behandlingsId = tilAttesteringSøknadsbehandling.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingsoversikt.Behandlingsstatus.TIL_ATTESTERING,
                    behandlingStartet = tilAttesteringSøknadsbehandling.opprettet,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo, fixedClock)
        val sakerMedÅpneBehandlinger = sakService.hentÅpneBehandlingerForAlleSaker()

        sakerMedÅpneBehandlinger shouldBe listOf(
            Behandlingsoversikt(
                saksnummer = saksnr1,
                behandlingsId = uavklartSøkandsbehandling.id,
                behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                status = Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING,
                behandlingStartet = uavklartSøkandsbehandling.opprettet,
            ),
            Behandlingsoversikt(
                saksnummer = saksnr1,
                behandlingsId = underkjentSøknadsbehandling.id,
                behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                status = Behandlingsoversikt.Behandlingsstatus.UNDERKJENT,
                behandlingStartet = underkjentSøknadsbehandling.opprettet,
            ),
            Behandlingsoversikt(
                saksnummer = saksnr2,
                behandlingsId = tilAttesteringSøknadsbehandling.id,
                behandlingstype = Behandlingsoversikt.Behandlingstype.SØKNADSBEHANDLING,
                status = Behandlingsoversikt.Behandlingsstatus.TIL_ATTESTERING,
                behandlingStartet = tilAttesteringSøknadsbehandling.opprettet,
            ),
        )
    }

    @Test
    fun `henter bare åpne revurderinger på en sak`() {
        val saknr1 = Saksnummer(2021)
        val saknr2 = Saksnummer(2022)

        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(saknr1).second
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(saknr1).second

        val underkjentInnvilgetRevurdering =
            underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(saknr2).second
        val tilAttesteringRevurdering =
            tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(saknr2).second

        val sakRepo: SakRepo = mock {
            on { hentÅpneBehandlinger() } doReturn listOf(
                Behandlingsoversikt(
                    saksnummer = saknr1,
                    behandlingsId = opprettetRevurdering.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = opprettetRevurdering.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = saknr1,
                    behandlingsId = simulertRevurdering.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = simulertRevurdering.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = saknr2,
                    behandlingsId = underkjentInnvilgetRevurdering.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    status = Behandlingsoversikt.Behandlingsstatus.UNDERKJENT,
                    behandlingStartet = underkjentInnvilgetRevurdering.opprettet,
                ),
                Behandlingsoversikt(
                    saksnummer = saknr2,
                    behandlingsId = tilAttesteringRevurdering.id,
                    behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                    status = Behandlingsoversikt.Behandlingsstatus.TIL_ATTESTERING,
                    behandlingStartet = tilAttesteringRevurdering.opprettet,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo, fixedClock)
        val sakerMedÅpneRevurderinger = sakService.hentÅpneBehandlingerForAlleSaker()

        sakerMedÅpneRevurderinger shouldBe listOf(
            Behandlingsoversikt(
                saksnummer = saknr1,
                behandlingsId = opprettetRevurdering.id,
                behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                status = Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING,
                behandlingStartet = opprettetRevurdering.opprettet,
            ),
            Behandlingsoversikt(
                saksnummer = saknr1,
                behandlingsId = simulertRevurdering.id,
                behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                status = Behandlingsoversikt.Behandlingsstatus.UNDER_BEHANDLING,
                behandlingStartet = simulertRevurdering.opprettet,
            ),
            Behandlingsoversikt(
                saksnummer = saknr2,
                behandlingsId = underkjentInnvilgetRevurdering.id,
                behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                status = Behandlingsoversikt.Behandlingsstatus.UNDERKJENT,
                behandlingStartet = underkjentInnvilgetRevurdering.opprettet,
            ),
            Behandlingsoversikt(
                saksnummer = saknr2,
                behandlingsId = tilAttesteringRevurdering.id,
                behandlingstype = Behandlingsoversikt.Behandlingstype.REVURDERING,
                status = Behandlingsoversikt.Behandlingsstatus.TIL_ATTESTERING,
                behandlingStartet = tilAttesteringRevurdering.opprettet,
            ),
        )
    }
}
