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
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.ÅpenBehandlingStatus
import no.nav.su.se.bakover.domain.behandling.ÅpenBehandlingType
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.opprettetRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.simulertRevurderingInnvilget
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknadsbehandling.lagIverksattInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.lagTilAttesteringInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.lagUavklartSøknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.lagUnderkjentInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.test.IverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.UnderkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlignsVedtak
import no.nav.su.se.bakover.test.fixedClock
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
                        Søknad.Journalført.MedOppgave(
                            id = UUID.randomUUID(), opprettet = Tidspunkt.EPOCH, sakId = sakId,
                            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                            journalpostId = JournalpostId(value = "123"), oppgaveId = OppgaveId(value = "123"),
                        ),
                        Søknad.Lukket(
                            id = UUID.randomUUID(), opprettet = Tidspunkt.EPOCH,
                            sakId = sakId, søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                            journalpostId = null, oppgaveId = null,
                            lukketTidspunkt = Tidspunkt.EPOCH,
                            lukketAv = NavIdentBruker.Saksbehandler(navIdent = "123"),
                            lukketType = Søknad.Lukket.LukketType.AVVIST,
                            lukketJournalpostId = null, lukketBrevbestillingId = null,
                        ),
                    ),
                    behandlinger = emptyList(), utbetalinger = emptyList(),
                    revurderinger = emptyList(), vedtakListe = emptyList(),
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakMedÅpenSøknad = sakService.hentÅpneBehandlingerForAlleSaker()

        sakMedÅpenSøknad.size shouldBe 1
        sakMedÅpenSøknad.first().saksnummer shouldBe Saksnummer(2021)
        sakMedÅpenSøknad.first().åpneBehandlinger.let {
            it.size shouldBe 1
            it.first().åpenBehandlingType shouldBe ÅpenBehandlingType.SØKNADSBEHANDLING
            it.first().status shouldBe ÅpenBehandlingStatus.NY_SØKNAD
            it.first().opprettet shouldBe Tidspunkt.EPOCH
        }
    }

    @Test
    fun `henter bare åpne søknadsbehandlinger på en sak`() {
        val sakId = UUID.randomUUID()
        val sakRepo: SakRepo = mock {
            on { hentAlleSaker() } doReturn listOf(
                Sak(
                    id = sakId, saksnummer = Saksnummer(nummer = 2021),
                    opprettet = Tidspunkt.EPOCH, fnr = FnrGenerator.random(),
                    søknader = emptyList(),
                    behandlinger = listOf(
                        lagUavklartSøknadsbehandling(sakId, Saksnummer(2021)),
                        lagUnderkjentInnvilgetSøknadsbehandling(sakId, Saksnummer(2021)),
                    ),
                    utbetalinger = emptyList(), revurderinger = emptyList(), vedtakListe = emptyList(),
                ),
                Sak(
                    id = sakId, saksnummer = Saksnummer(nummer = 2022),
                    opprettet = Tidspunkt.EPOCH, fnr = FnrGenerator.random(),
                    søknader = emptyList(),
                    behandlinger = listOf(
                        lagTilAttesteringInnvilgetSøknadsbehandling(sakId, Saksnummer(2022)),
                        lagIverksattInnvilgetSøknadsbehandling(sakId, Saksnummer(2022)),
                    ),
                    utbetalinger = emptyList(), revurderinger = emptyList(), vedtakListe = emptyList(),
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakerMedÅpneBehandlinger = sakService.hentÅpneBehandlingerForAlleSaker()

        sakerMedÅpneBehandlinger.size shouldBe 2
        sakerMedÅpneBehandlinger.first().saksnummer shouldBe Saksnummer(2021)
        sakerMedÅpneBehandlinger.first().åpneBehandlinger.size shouldBe 2
        sakerMedÅpneBehandlinger.first().åpneBehandlinger.let {
            it.first().åpenBehandlingType shouldBe ÅpenBehandlingType.SØKNADSBEHANDLING
            it.first().status shouldBe ÅpenBehandlingStatus.UNDER_BEHANDLING
            it.first().opprettet shouldBe Tidspunkt.EPOCH
            it.last().åpenBehandlingType shouldBe ÅpenBehandlingType.SØKNADSBEHANDLING
            it.last().status shouldBe ÅpenBehandlingStatus.UNDERKJENT
            it.last().opprettet shouldBe Tidspunkt.EPOCH
        }
        sakerMedÅpneBehandlinger.last().saksnummer shouldBe Saksnummer(2022)
        sakerMedÅpneBehandlinger.last().åpneBehandlinger.let {
            it.size shouldBe 1
            it.first().åpenBehandlingType shouldBe ÅpenBehandlingType.SØKNADSBEHANDLING
            it.first().status shouldBe ÅpenBehandlingStatus.TIL_ATTESTERING
            it.first().opprettet shouldBe Tidspunkt.EPOCH
        }
    }

    @Test
    fun `henter bare åpne revurderinger på en sak`() {
        val sakId = UUID.randomUUID()
        val sakRepo: SakRepo = mock {
            on { hentAlleSaker() } doReturn listOf(
                Sak(
                    id = sakId, saksnummer = Saksnummer(nummer = 2021),
                    opprettet = Tidspunkt.EPOCH, fnr = FnrGenerator.random(),
                    søknader = emptyList(), behandlinger = emptyList(),
                    utbetalinger = emptyList(), vedtakListe = emptyList(),
                    revurderinger = listOf(
                        opprettetRevurdering,
                        simulertRevurderingInnvilget,
                    ),
                ),
                Sak(
                    id = sakId, saksnummer = Saksnummer(nummer = 2022),
                    opprettet = Tidspunkt.EPOCH, fnr = FnrGenerator.random(),
                    søknader = emptyList(), behandlinger = emptyList(),
                    utbetalinger = emptyList(), vedtakListe = emptyList(),
                    revurderinger = listOf(
                        UnderkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlignsVedtak(),
                        tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
                        IverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(),
                    ),
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo)
        val sakerMedÅpneRevurderinger = sakService.hentÅpneBehandlingerForAlleSaker()

        sakerMedÅpneRevurderinger.size shouldBe 2
        sakerMedÅpneRevurderinger.first().saksnummer shouldBe Saksnummer(2021)
        sakerMedÅpneRevurderinger.first().åpneBehandlinger.size shouldBe 2
        sakerMedÅpneRevurderinger.first().åpneBehandlinger.let {
            it.first().åpenBehandlingType shouldBe ÅpenBehandlingType.REVURDERING
            it.first().status shouldBe ÅpenBehandlingStatus.UNDER_BEHANDLING
            it.first().opprettet shouldBe Tidspunkt.now(fixedClock)
            it.last().åpenBehandlingType shouldBe ÅpenBehandlingType.REVURDERING
            it.last().status shouldBe ÅpenBehandlingStatus.UNDER_BEHANDLING
            it.last().opprettet shouldBe Tidspunkt.now(fixedClock)
        }
        sakerMedÅpneRevurderinger.last().saksnummer shouldBe Saksnummer(2022)
        sakerMedÅpneRevurderinger.last().åpneBehandlinger.let {
            it.size shouldBe 2
            it.first().åpenBehandlingType shouldBe ÅpenBehandlingType.REVURDERING
            it.first().status shouldBe ÅpenBehandlingStatus.UNDERKJENT
            it.first().opprettet shouldBe Tidspunkt.now(fixedClock)
            it.last().åpenBehandlingType shouldBe ÅpenBehandlingType.REVURDERING
            it.last().status shouldBe ÅpenBehandlingStatus.TIL_ATTESTERING
            it.last().opprettet shouldBe Tidspunkt.now(fixedClock)
        }
    }
}
