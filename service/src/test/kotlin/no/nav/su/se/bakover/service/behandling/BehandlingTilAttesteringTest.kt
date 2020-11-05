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
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.createService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import org.junit.jupiter.api.Test
import java.util.UUID

class BehandlingTilAttesteringTest {
    val sakId = UUID.randomUUID()
    val fnr = FnrGenerator.random()
    val oppgaveId = OppgaveId("123")
    val nyOppgaveId = OppgaveId("999")
    val aktørId = AktørId("12345")

    private val beregning = Beregning(
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.januar(2020),
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

    private val simulertBehandling = BehandlingFactory(mock()).createBehandling(
        sakId = sakId,
        søknad = Søknad(sakId = sakId, søknadInnhold = SøknadInnholdTestdataBuilder.build(), oppgaveId = oppgaveId),
        status = Behandling.BehandlingsStatus.SIMULERT,
        beregning = beregning,
        fnr = fnr,
        simulering = simulering,
        oppgaveId = oppgaveId
    )

    private val saksbehandler = NavIdentBruker.Saksbehandler("Z12345")

    @Test
    fun `sjekk at vi sender inn riktig oppgaveId ved lukking av oppgave ved attestering`() {
        val behandling = simulertBehandling.copy()

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

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personOppslag = personOppslagMock,
            oppgaveService = oppgaveServiceMock
        ).sendTilAttestering(behandling.id, saksbehandler)

        actual shouldBe simulertBehandling.copy(
            saksbehandler = saksbehandler,
            status = Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET
        ).right()

        inOrder(behandlingRepoMock, personOppslagMock, oppgaveServiceMock) {
            verify(behandlingRepoMock).hentBehandling(simulertBehandling.id)
            verify(personOppslagMock).aktørId(fnr)
            verify(oppgaveServiceMock).opprettOppgave(
                config = OppgaveConfig.Attestering(
                    sakId = sakId.toString(),
                    aktørId = aktørId
                )
            )

            verify(behandlingRepoMock).settSaksbehandler(simulertBehandling.id, saksbehandler)
            verify(behandlingRepoMock).oppdaterBehandlingStatus(simulertBehandling.id, Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET)

            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
        }
        verifyNoMoreInteractions(behandlingRepoMock, personOppslagMock, oppgaveServiceMock)
    }
}
