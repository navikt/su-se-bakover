package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadsbehandlingServiceOpprettetTest {

    @Test
    fun `svarer med feil dersom vi ikke finner søknad`() {
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn FantIkkeSøknad.left()
        }

        val saksbehandlingRepo = mock<SøknadsbehandlingRepo>()

        val service = createSøknadsbehandlingService(
            søknadsbehandlingRepo = saksbehandlingRepo,
            søknadService = søknadServiceMock,
        )

        val søknadId = UUID.randomUUID()
        service.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = søknadId,
            ),
        ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.FantIkkeSøknad.left()

        verify(søknadServiceMock).hentSøknad(søknadId)
        verifyNoMoreInteractions(søknadServiceMock, saksbehandlingRepo)
    }

    @Test
    fun `svarer med feil dersom søknad allrede er lukket`() {
        val lukketSøknad = Søknad.Lukket(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = null,
            oppgaveId = null,
            lukketTidspunkt = Tidspunkt.now(),
            lukketAv = NavIdentBruker.Saksbehandler("sas"),
            lukketType = Søknad.Lukket.LukketType.BORTFALT,
            lukketJournalpostId = null,
            lukketBrevbestillingId = null,
        )

        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn lukketSøknad.right()
        }

        val saksbehandlingRepo = mock<SøknadsbehandlingRepo>()

        val service = createSøknadsbehandlingService(
            søknadsbehandlingRepo = saksbehandlingRepo,
            søknadService = søknadServiceMock,
        )

        service.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = lukketSøknad.id,
            ),
        ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.SøknadErLukket.left()

        verify(søknadServiceMock).hentSøknad(lukketSøknad.id)
        verifyNoMoreInteractions(søknadServiceMock, saksbehandlingRepo)
    }

    @Test
    fun `svarer med feil dersom søknad ikke er journalført med oppgave`() {
        val utenJournalpostOgOppgave = Søknad.Ny(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
        )

        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn utenJournalpostOgOppgave.right()
        }

        val saksbehandlingRepo = mock<SøknadsbehandlingRepo>()

        val service = createSøknadsbehandlingService(
            søknadsbehandlingRepo = saksbehandlingRepo,
            søknadService = søknadServiceMock,
        )

        service.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = utenJournalpostOgOppgave.id,
            ),
        ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.SøknadManglerOppgave.left()

        verify(søknadServiceMock).hentSøknad(utenJournalpostOgOppgave.id)
        verifyNoMoreInteractions(søknadServiceMock, saksbehandlingRepo)
    }

    @Test
    fun `svarer med feil dersom søknad har påbegynt behandling`() {
        val søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId(value = "2"),
            oppgaveId = OppgaveId(value = "1"),
        )
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadRepoMock = mock<SøknadRepo> {
            on { harSøknadPåbegyntBehandling(any()) } doReturn true
        }

        val saksbehandlingRepo = mock<SøknadsbehandlingRepo>()

        val service = createSøknadsbehandlingService(
            søknadsbehandlingRepo = saksbehandlingRepo,
            søknadService = søknadServiceMock,
            søknadRepo = søknadRepoMock,
        )

        service.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = søknad.id,
            ),
        ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.SøknadHarAlleredeBehandling.left()

        verify(søknadServiceMock).hentSøknad(søknad.id)
        verify(søknadRepoMock).harSøknadPåbegyntBehandling(søknad.id)
        verifyNoMoreInteractions(søknadServiceMock, saksbehandlingRepo)
    }

    @Test
    fun `Oppretter behandling og publiserer event`() {
        val sakId = UUID.randomUUID()
        val stønadsperiode = ValgtStønadsperiode(Periode.create(1.januar(2021), 31.desember(2021)))
        val søknadInnhold = SøknadInnholdTestdataBuilder.build()
        val fnr = søknadInnhold.personopplysninger.fnr
        val søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            søknadInnhold = søknadInnhold,
            journalpostId = JournalpostId(value = "2"),
            oppgaveId = OppgaveId(value = "1"),
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
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )
        val søknadService: SøknadService = mock {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadRepo: SøknadRepo = mock {
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val søknadsbehandlingRepoMock: SøknadsbehandlingRepo = mock {
            on { lagre(any()) }.doNothing()
            on { hent(any()) } doReturn expectedSøknadsbehandling
        }
        val behandlingService = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = mock(),
            oppgaveService = mock(),
            søknadService = søknadService,
            søknadRepo = søknadRepo,
            personService = mock(),
            behandlingMetrics = mock(),
            beregningService = mock(),
        )
        val eventObserver: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }
        behandlingService.addObserver(eventObserver)

        behandlingService.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = søknad.id,
            ),
        ).orNull()!!.shouldBeEqualToIgnoringFields(
            expectedSøknadsbehandling,
            Søknadsbehandling.Vilkårsvurdert.Uavklart::id,
            Søknadsbehandling.Vilkårsvurdert.Uavklart::opprettet,
        )
        verify(søknadService).hentSøknad(argThat { it shouldBe søknad.id })
        verify(søknadRepo).harSøknadPåbegyntBehandling(argThat { it shouldBe søknad.id })

        val persistertSøknadsbehandling = argumentCaptor<Søknadsbehandling.Vilkårsvurdert.Uavklart>()

        verify(søknadsbehandlingRepoMock).lagre(persistertSøknadsbehandling.capture())

        verify(søknadsbehandlingRepoMock).hent(
            argThat { it shouldBe persistertSøknadsbehandling.firstValue.id },
        )
        verify(eventObserver).handle(
            argThat {
                it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingOpprettet(
                    expectedSøknadsbehandling,
                )
            },
        )
    }
}
