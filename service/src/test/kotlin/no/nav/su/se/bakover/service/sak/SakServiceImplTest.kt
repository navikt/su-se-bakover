package no.nav.su.se.bakover.service.sak

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.person.IdentClient
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.sak.OpprettDokumentRequest
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjon
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksbehandlerNavn
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tikkendeFixedClock
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

        val observer: StatistikkEventObserver = mock()

        val sakService = SakServiceImpl(sakRepo, fixedClock, mock(), mock(), mock(), mock())
        sakService.addObserver(observer)
        sakService.opprettSak(mock { on { id } doReturn sakId })

        verify(sakRepo).opprettSak(any())
        verify(sakRepo).hentSak(sak.id)
        verify(observer).handle(argThat { it shouldBe StatistikkEvent.SakOpprettet(sak) })
    }

    @Test
    fun `Publiserer ikke event ved feil av opprettelse av sak`() {
        val sakRepo: SakRepo = mock {
            on { opprettSak(any()) } doThrow RuntimeException("hehe exception")
        }

        val observer: StatistikkEventObserver = mock()

        val sakService = SakServiceImpl(sakRepo, fixedClock, mock(), mock(), mock(), mock())
        sakService.addObserver(observer)
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

        val sakService = SakServiceImpl(sakRepo, fixedClock, mock(), mock(), mock(), mock())
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

        val sakService = SakServiceImpl(sakRepo, fixedClock, mock(), mock(), mock(), mock())
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

        val clock = tikkendeFixedClock()

        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            saksnummer = saknr1,
            clock = clock,
        ).second
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            saksnummer = saknr1,
            clock = clock,
        ).second

        val underkjentInnvilgetRevurdering =
            underkjentInnvilgetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
                saksnummer = saknr2,
                clock = clock,
            ).second
        val tilAttesteringRevurdering =
            tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
                saksnummer = saknr2,
                clock = clock,
            ).second

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

        val sakService = SakServiceImpl(sakRepo, fixedClock, mock(), mock(), mock(), mock())
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

    @Test
    fun `oppretter fritekst dokument`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val sakRepo: SakRepo = mock {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val brevService = mock<BrevService> {
            on { lagDokument(any<LagBrevRequest>()) } doReturn dokumentUtenMetadataInformasjon().right()
        }

        val personService = mock<PersonService> {
            on { hentPerson(any()) } doReturn person(sak.fnr, AktørId("aktørId")).right()
        }
        val identClient = mock<IdentClient> {
            on { hentNavnForNavIdent(any()) } doReturn saksbehandlerNavn.right()
        }

        SakServiceImpl(sakRepo, fixedClock, mock(), brevService, personService, identClient).opprettFritekstDokument(
            request = OpprettDokumentRequest(
                sakId = sak.id,
                saksbehandler = saksbehandler,
                tittel = "Brev tittel",
                fritekst = "Brev fritekst",
            ),
        ).shouldBeRight()
    }
}
