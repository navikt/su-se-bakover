package no.nav.su.se.bakover.service.behandling

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import org.junit.jupiter.api.Test
import java.util.UUID

class UnderkjennBehandlingTest {
    private val sakId = UUID.randomUUID()
    private val fnr = FnrGenerator.random()
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    private val nyOppgaveId = OppgaveId("999")
    private val aktørId = AktørId("12345")
    private val begrunnelse = "begrunnelse"
    private val attestant = NavIdentBruker.Attestant("a")
    private val saksbehandler = NavIdentBruker.Saksbehandler("s")

    private val beregning = BeregningFactory.ny(
        periode = Periode(1.januar(2020), 31.januar(2020)),
        sats = Sats.HØY,
        fradrag = listOf()
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(),
        nettoBeløp = 191500,
        periodeList = listOf()
    )

    private val innvilgetBehandlingTilAttestering = BehandlingFactory(mock()).createBehandling(
        sakId = sakId,
        søknad = Søknad.Journalført.MedOppgave(
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = journalpostId
        ),
        status = Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
        beregning = beregning,
        fnr = fnr,
        simulering = simulering,
        oppgaveId = oppgaveId,
        attestant = attestant,
        saksbehandler = saksbehandler
    )

    @Test
    fun `underkjenner behandling`() {
        val behandling: Behandling = innvilgetBehandlingTilAttestering.copy()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
            on { settSaksbehandler(any(), any()) } doReturn behandling
            on { oppdaterBehandlingStatus(any(), any()) } doReturn behandling
        }

        val personOppslagMock: PersonOppslag = mock {
            on { aktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val hendelsesloggRepoMock = mock<HendelsesloggRepo>()

        val actual = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            hendelsesloggRepo = hendelsesloggRepoMock
        ).underkjenn(
            behandlingId = behandling.id,
            attestant = attestant,
            begrunnelse = begrunnelse
        )

        actual shouldBe behandling.copy(
            status = Behandling.BehandlingsStatus.SIMULERT
        ).right()

        inOrder(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        ) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(personOppslagMock).aktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe oppgaveId })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Saksbehandling(
                        journalpostId = journalpostId,
                        sakId = sakId,
                        aktørId = aktørId
                    )
                }
            )
            verify(behandlingMetricsMock).incrementUnderkjentCounter()
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandlingId = argThat { it shouldBe innvilgetBehandlingTilAttestering.id },
                status = argThat { it shouldBe Behandling.BehandlingsStatus.SIMULERT }
            )

            verify(hendelsesloggRepoMock).oppdaterHendelseslogg(
                argThat {
                    it shouldBe Hendelseslogg(
                        id = innvilgetBehandlingTilAttestering.id.toString(),
                        hendelser = mutableListOf(
                            UnderkjentAttestering(
                                attestant.navIdent,
                                begrunnelse,
                                it.hendelser()[0].tidspunkt
                            )
                        )
                    )
                }
            )
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            behandlingRepoMock,
            personOppslagMock,
            oppgaveServiceMock,
            behandlingMetricsMock,
            hendelsesloggRepoMock
        )
    }
}
