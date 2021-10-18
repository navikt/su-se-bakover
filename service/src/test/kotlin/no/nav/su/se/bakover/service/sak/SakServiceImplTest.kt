package no.nav.su.se.bakover.service.sak

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.sak.SakRestans
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.test.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlignsVedtak
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

        val sakService = SakServiceImpl(sakRepo)
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

        val sakService = SakServiceImpl(sakRepo)
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
            on { hentSakRestanser() } doReturn listOf(
                SakRestans(
                    saksnummer = Saksnummer(nummer = 2021),
                    behandlingsId = nySakMedjournalførtSøknadOgOppgave.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.NY_SØKNAD,
                    behandlingStartet = nySakMedjournalførtSøknadOgOppgave.opprettet,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakMedÅpenSøknad = sakService.hentRestanserForAlleSaker()

        sakMedÅpenSøknad shouldBe listOf(
            SakRestans(
                saksnummer = Saksnummer(nummer = 2021),
                behandlingsId = nySakMedjournalførtSøknadOgOppgave.id,
                restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                status = SakRestans.RestansStatus.NY_SØKNAD,
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
            on { hentSakRestanser() } doReturn listOf(
                SakRestans(
                    saksnummer = saksnr1,
                    behandlingsId = uavklartSøkandsbehandling.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                    behandlingStartet = uavklartSøkandsbehandling.opprettet,
                ),
                SakRestans(
                    saksnummer = saksnr1,
                    behandlingsId = underkjentSøknadsbehandling.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.UNDERKJENT,
                    behandlingStartet = underkjentSøknadsbehandling.opprettet,
                ),
                SakRestans(
                    saksnummer = saksnr2,
                    behandlingsId = tilAttesteringSøknadsbehandling.id,
                    restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                    status = SakRestans.RestansStatus.TIL_ATTESTERING,
                    behandlingStartet = tilAttesteringSøknadsbehandling.opprettet,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakerMedÅpneBehandlinger = sakService.hentRestanserForAlleSaker()

        sakerMedÅpneBehandlinger shouldBe listOf(
            SakRestans(
                saksnummer = saksnr1,
                behandlingsId = uavklartSøkandsbehandling.id,
                restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                behandlingStartet = uavklartSøkandsbehandling.opprettet,
            ),
            SakRestans(
                saksnummer = saksnr1,
                behandlingsId = underkjentSøknadsbehandling.id,
                restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                status = SakRestans.RestansStatus.UNDERKJENT,
                behandlingStartet = underkjentSøknadsbehandling.opprettet,
            ),
            SakRestans(
                saksnummer = saksnr2,
                behandlingsId = tilAttesteringSøknadsbehandling.id,
                restansType = SakRestans.RestansType.SØKNADSBEHANDLING,
                status = SakRestans.RestansStatus.TIL_ATTESTERING,
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
            underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlignsVedtak(saknr2).second
        val tilAttesteringRevurdering =
            tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(saknr2).second

        val sakRepo: SakRepo = mock {
            on { hentSakRestanser() } doReturn listOf(
                SakRestans(
                    saksnummer = saknr1,
                    behandlingsId = opprettetRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                    behandlingStartet = opprettetRevurdering.opprettet,
                ),
                SakRestans(
                    saksnummer = saknr1,
                    behandlingsId = simulertRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                    behandlingStartet = simulertRevurdering.opprettet,
                ),
                SakRestans(
                    saksnummer = saknr2,
                    behandlingsId = underkjentInnvilgetRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.UNDERKJENT,
                    behandlingStartet = underkjentInnvilgetRevurdering.opprettet,
                ),
                SakRestans(
                    saksnummer = saknr2,
                    behandlingsId = tilAttesteringRevurdering.id,
                    restansType = SakRestans.RestansType.REVURDERING,
                    status = SakRestans.RestansStatus.TIL_ATTESTERING,
                    behandlingStartet = tilAttesteringRevurdering.opprettet,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakerMedÅpneRevurderinger = sakService.hentRestanserForAlleSaker()

        sakerMedÅpneRevurderinger shouldBe listOf(
            SakRestans(
                saksnummer = saknr1,
                behandlingsId = opprettetRevurdering.id,
                restansType = SakRestans.RestansType.REVURDERING,
                status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                behandlingStartet = opprettetRevurdering.opprettet,
            ),
            SakRestans(
                saksnummer = saknr1,
                behandlingsId = simulertRevurdering.id,
                restansType = SakRestans.RestansType.REVURDERING,
                status = SakRestans.RestansStatus.UNDER_BEHANDLING,
                behandlingStartet = simulertRevurdering.opprettet,
            ),
            SakRestans(
                saksnummer = saknr2,
                behandlingsId = underkjentInnvilgetRevurdering.id,
                restansType = SakRestans.RestansType.REVURDERING,
                status = SakRestans.RestansStatus.UNDERKJENT,
                behandlingStartet = underkjentInnvilgetRevurdering.opprettet,
            ),
            SakRestans(
                saksnummer = saknr2,
                behandlingsId = tilAttesteringRevurdering.id,
                restansType = SakRestans.RestansType.REVURDERING,
                status = SakRestans.RestansStatus.TIL_ATTESTERING,
                behandlingStartet = tilAttesteringRevurdering.opprettet,
            ),
        )
    }
}
