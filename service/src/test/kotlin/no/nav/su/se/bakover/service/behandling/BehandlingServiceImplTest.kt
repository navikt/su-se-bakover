package no.nav.su.se.bakover.service.behandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import org.junit.jupiter.api.Test
import java.util.UUID

internal class BehandlingServiceImplTest {

    private val sakId = UUID.randomUUID()
    private val søknadId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")
    private val saksbehandler = Saksbehandler("AB12345")
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    private val behandlingMetricsMock = mock<BehandlingMetrics>()
    private fun opprettetBehandling(): Behandling {
        return BehandlingFactory(behandlingMetricsMock).createBehandling(
            id = behandlingId,
            opprettet = BehandlingTestUtils.tidspunkt,
            sakId = sakId,
            søknad = Søknad.Journalført.MedOppgave(
                id = søknadId,
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = oppgaveId,
                journalpostId = journalpostId,
            ),
            status = Behandling.BehandlingsStatus.OPPRETTET,
            fnr = fnr,
            oppgaveId = oppgaveId,
            behandlingsinformasjon = BehandlingTestUtils.behandlingsinformasjon,
            simulering = null,
            beregning = null,
        )
    }

    @Test
    fun `kan ikke hente behandling`() {
        val behandlingInformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn null
        }

        val response = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
        ).oppdaterBehandlingsinformasjon(behandlingId, saksbehandler, behandlingInformasjon)

        response shouldBe KunneIkkeOppdatereBehandlingsinformasjon.FantIkkeBehandling.left()

        verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `attestant som har underkjent og saksbehandler kan ikke være den samme`() {
        val behandlingInformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn opprettetBehandling().copy(attestant = Attestant(saksbehandler.navIdent))
        }

        val response = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
        ).oppdaterBehandlingsinformasjon(behandlingId, saksbehandler, behandlingInformasjon)

        response shouldBe KunneIkkeOppdatereBehandlingsinformasjon.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `happy case`() {
        val behandlingInformasjon = Behandlingsinformasjon(
            uførhet = Behandlingsinformasjon.Uførhet(
                status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                uføregrad = 20,
                forventetInntekt = 10
            ),
            flyktning = Behandlingsinformasjon.Flyktning(
                status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
                begrunnelse = null
            ),
            lovligOpphold = Behandlingsinformasjon.LovligOpphold(
                status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
                begrunnelse = null
            ),
            fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
                status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
                begrunnelse = null
            ),
            oppholdIUtlandet = Behandlingsinformasjon.OppholdIUtlandet(
                status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
                begrunnelse = null
            ),
            formue = Behandlingsinformasjon.Formue(
                status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                verdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0
                ),
                ektefellesVerdier = Behandlingsinformasjon.Formue.Verdier(
                    verdiIkkePrimærbolig = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0
                ),
                begrunnelse = null
            ),
            personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
                status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
                begrunnelse = null
            ),
            bosituasjon = Behandlingsinformasjon.Bosituasjon(
                epsFnr = null,
                delerBolig = false,
                ektemakeEllerSamboerUførFlyktning = false,
                begrunnelse = null
            ),
            ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.Ektefelle(
                fnr = Fnr("17087524256"),
                navn = Person.Navn(fornavn = "fornavn", mellomnavn = null, etternavn = "etternavn"),
                kjønn = null,
                adressebeskyttelse = null,
                skjermet = null
            )
        )

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn opprettetBehandling()
        }

        val response = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
        ).oppdaterBehandlingsinformasjon(behandlingId, saksbehandler, behandlingInformasjon)

        response shouldBe opprettetBehandling().copy(status = VILKÅRSVURDERT_INNVILGET).right()

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandlingId })
            verify(behandlingRepoMock).slettBeregning(
                argThat { it shouldBe behandlingId }
            )
            verify(behandlingRepoMock).oppdaterBehandlingsinformasjon(
                argThat { it shouldBe behandlingId },
                argThat { it shouldBe behandlingInformasjon }
            )
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                argThat { it shouldBe behandlingId },
                argThat { it shouldBe VILKÅRSVURDERT_INNVILGET }
            )
        }
        verifyNoMoreInteractions(behandlingRepoMock)
    }
}
