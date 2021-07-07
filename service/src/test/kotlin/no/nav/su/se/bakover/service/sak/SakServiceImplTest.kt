package no.nav.su.se.bakover.service.sak

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.ÅpenBehandling
import no.nav.su.se.bakover.domain.behandling.ÅpenBehandlingStatus
import no.nav.su.se.bakover.domain.behandling.ÅpenBehandlingType
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.test.IverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.UnderkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlignsVedtak
import no.nav.su.se.bakover.test.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.test.lukketSøknad
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
            verifyZeroInteractions(observer)
        }
    }

    @Test
    fun `henter bare åpen søknad på en sak`() {
        val sakId = UUID.randomUUID()
        val sakRepo: SakRepo = mock {
            on { hentAlleSaker() } doReturn listOf(
                Sak(
                    id = sakId, saksnummer = Saksnummer(nummer = 2021),
                    opprettet = Tidspunkt.EPOCH, fnr = FnrGenerator.random(),
                    søknader = listOf(
                        journalførtSøknadMedOppgave,
                        lukketSøknad,
                    ),
                    behandlinger = emptyList(), utbetalinger = emptyList(),
                    revurderinger = emptyList(), vedtakListe = emptyList(),
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakMedÅpenSøknad = sakService.hentÅpneBehandlingerForAlleSaker()

        sakMedÅpenSøknad shouldBe listOf(
            ÅpenBehandling(
                saksnummer = Saksnummer(nummer = 2021),
                behandlingsId = journalførtSøknadMedOppgave.id,
                åpenBehandlingType = ÅpenBehandlingType.SØKNADSBEHANDLING,
                status = ÅpenBehandlingStatus.NY_SØKNAD,
                opprettet = journalførtSøknadMedOppgave.opprettet,
            ),
        )
    }

    @Test
    fun `henter bare åpne søknadsbehandlinger på en sak`() {
        val sakId = UUID.randomUUID()

        val saksnr1 = Saksnummer(2021)
        val saksnr2 = Saksnummer(2022)

        val uavklartSøkandsbehandling = søknadsbehandlingVilkårsvurdertUavklart(saksnr1)
        val underkjentSøknadsbehandling = søknadsbehandlingUnderkjentInnvilget(saksnr1)
        val tilAttesteringSøknadsbehandling = søknadsbehandlingTilAttesteringInnvilget(saksnr2)
        val iverksattSøknadsbehandling = søknadsbehandlingIverksattInnvilget(saksnr2)

        val sakRepo: SakRepo = mock {
            on { hentAlleSaker() } doReturn listOf(
                Sak(
                    id = sakId, saksnummer = saksnr1,
                    opprettet = Tidspunkt.EPOCH, fnr = FnrGenerator.random(),
                    søknader = emptyList(),
                    behandlinger = listOf(
                        uavklartSøkandsbehandling,
                        underkjentSøknadsbehandling,
                    ),
                    utbetalinger = emptyList(), revurderinger = emptyList(), vedtakListe = emptyList(),
                ),
                Sak(
                    id = sakId, saksnummer = saksnr2,
                    opprettet = Tidspunkt.EPOCH, fnr = FnrGenerator.random(),
                    søknader = emptyList(),
                    behandlinger = listOf(
                        tilAttesteringSøknadsbehandling,
                        iverksattSøknadsbehandling,
                    ),
                    utbetalinger = emptyList(), revurderinger = emptyList(), vedtakListe = emptyList(),
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakerMedÅpneBehandlinger = sakService.hentÅpneBehandlingerForAlleSaker()

        sakerMedÅpneBehandlinger shouldBe listOf(
            ÅpenBehandling(
                saksnummer = saksnr1,
                behandlingsId = uavklartSøkandsbehandling.id,
                åpenBehandlingType = ÅpenBehandlingType.SØKNADSBEHANDLING,
                status = ÅpenBehandlingStatus.UNDER_BEHANDLING,
                opprettet = uavklartSøkandsbehandling.opprettet,
            ),
            ÅpenBehandling(
                saksnummer = saksnr1,
                behandlingsId = underkjentSøknadsbehandling.id,
                åpenBehandlingType = ÅpenBehandlingType.SØKNADSBEHANDLING,
                status = ÅpenBehandlingStatus.UNDERKJENT,
                opprettet = underkjentSøknadsbehandling.opprettet,
            ),
            ÅpenBehandling(
                saksnummer = saksnr2,
                behandlingsId = tilAttesteringSøknadsbehandling.id,
                åpenBehandlingType = ÅpenBehandlingType.SØKNADSBEHANDLING,
                status = ÅpenBehandlingStatus.TIL_ATTESTERING,
                opprettet = tilAttesteringSøknadsbehandling.opprettet,
            ),
        )
    }

    @Test
    fun `henter bare åpne revurderinger på en sak`() {
        val sakId = UUID.randomUUID()

        val saknr1 = Saksnummer(2021)
        val saknr2 = Saksnummer(2022)

        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(saknr1)
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(saknr1)

        val underkjentInnvilgetRevurdering = UnderkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlignsVedtak(saknr2)
        val tilAttesteringRevurdering = tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(saknr2)
        val iverksattRevurdering = IverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(saknr2)

        val sakRepo: SakRepo = mock {
            on { hentAlleSaker() } doReturn listOf(
                Sak(
                    id = sakId, saksnummer = saknr1,
                    opprettet = Tidspunkt.EPOCH, fnr = FnrGenerator.random(),
                    søknader = emptyList(), behandlinger = emptyList(),
                    utbetalinger = emptyList(), vedtakListe = emptyList(),
                    revurderinger = listOf(opprettetRevurdering, simulertRevurdering),
                ),
                Sak(
                    id = sakId, saksnummer = saknr2,
                    opprettet = Tidspunkt.EPOCH, fnr = FnrGenerator.random(),
                    søknader = emptyList(), behandlinger = emptyList(),
                    utbetalinger = emptyList(), vedtakListe = emptyList(),
                    revurderinger = listOf(
                        underkjentInnvilgetRevurdering,
                        tilAttesteringRevurdering,
                        iverksattRevurdering,
                    ),
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakerMedÅpneRevurderinger = sakService.hentÅpneBehandlingerForAlleSaker()

        sakerMedÅpneRevurderinger shouldBe listOf(
            ÅpenBehandling(
                saksnummer = saknr1,
                behandlingsId = opprettetRevurdering.id,
                åpenBehandlingType = ÅpenBehandlingType.REVURDERING,
                status = ÅpenBehandlingStatus.UNDER_BEHANDLING,
                opprettet = opprettetRevurdering.opprettet,
            ),
            ÅpenBehandling(
                saksnummer = saknr1,
                behandlingsId = simulertRevurdering.id,
                åpenBehandlingType = ÅpenBehandlingType.REVURDERING,
                status = ÅpenBehandlingStatus.UNDER_BEHANDLING,
                opprettet = simulertRevurdering.opprettet,
            ),
            ÅpenBehandling(
                saksnummer = saknr2,
                behandlingsId = underkjentInnvilgetRevurdering.id,
                åpenBehandlingType = ÅpenBehandlingType.REVURDERING,
                status = ÅpenBehandlingStatus.UNDERKJENT,
                opprettet = underkjentInnvilgetRevurdering.opprettet,
            ),
            ÅpenBehandling(
                saksnummer = saknr2,
                behandlingsId = tilAttesteringRevurdering.id,
                åpenBehandlingType = ÅpenBehandlingType.REVURDERING,
                status = ÅpenBehandlingStatus.TIL_ATTESTERING,
                opprettet = tilAttesteringRevurdering.opprettet,
            ),
        )
    }
}
