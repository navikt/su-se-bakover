package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.OpprettSøknadsbehandlingRequest
import no.nav.su.se.bakover.service.SaksbehandlingServiceImpl
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OpprettSøknadsbehandlingTest {

    @Test
    fun `Oppretter behandling og publiserer event`() {
        val sakId = UUID.randomUUID()
        val søknadInnhold = SøknadInnholdTestdataBuilder.build()
        val fnr = søknadInnhold.personopplysninger.fnr
        val søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            søknadInnhold = søknadInnhold,
            journalpostId = JournalpostId(value = "2"),
            oppgaveId = OppgaveId(value = "1")
        )
        val expectedSøknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(), // blir ignorert eller overskrevet
            opprettet = Tidspunkt.EPOCH, // blir ignorert eller overskrevet
            sakId = sakId,
            saksnummer = Saksnummer(123),
            søknad = søknad,
            oppgaveId = søknad.oppgaveId,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = fnr,
        )
        val søknadService: SøknadService = mock {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadRepo: SøknadRepo = mock {
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val saksbehandlingRepoMock: SaksbehandlingRepo = mock {
            on { lagre(any()) }.doNothing()
            on { hent(any()) } doReturn expectedSøknadsbehandling
        }
        val behandlingService = SaksbehandlingServiceImpl(
            utbetalingService = mock(),
            oppgaveService = mock(),
            søknadService = søknadService,
            søknadRepo = søknadRepo,
            personService = mock(),
            saksbehandlingRepo = saksbehandlingRepoMock,
            iverksettSaksbehandlingService = mock(),
            behandlingMetrics = mock(),
            beregningService = mock(),
        )
        val eventObserver: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }
        behandlingService.addObserver(eventObserver)

        behandlingService.opprett(OpprettSøknadsbehandlingRequest(søknad.id)).orNull()!!.shouldBeEqualToIgnoringFields(
            expectedSøknadsbehandling,
            Søknadsbehandling.Vilkårsvurdert.Uavklart::id,
            Søknadsbehandling.Vilkårsvurdert.Uavklart::opprettet,
        )
        verify(søknadService).hentSøknad(argThat { it shouldBe søknad.id })
        verify(søknadRepo).harSøknadPåbegyntBehandling(argThat { it shouldBe søknad.id })

        val persistertSøknadsbehandling = argumentCaptor<Søknadsbehandling.Vilkårsvurdert.Uavklart>()

        verify(saksbehandlingRepoMock).lagre(persistertSøknadsbehandling.capture())

        verify(saksbehandlingRepoMock).hent(
            argThat { it shouldBe persistertSøknadsbehandling.firstValue.id }
        )
        verify(eventObserver).handle(
            argThat {
                it shouldBe Event.Statistikk.SøknadsbehandlingOpprettet(
                    expectedSøknadsbehandling
                )
            }
        )
    }
}
