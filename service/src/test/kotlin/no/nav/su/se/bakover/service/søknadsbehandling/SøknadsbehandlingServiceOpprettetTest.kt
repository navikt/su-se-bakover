package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.søknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySakMedNySøknad
import no.nav.su.se.bakover.test.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.søknadsbehandlingBeregnetInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.toSøknadsbehandling
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

internal class SøknadsbehandlingServiceOpprettetTest {

    @Test
    fun `svarer med feil dersom vi ikke finner søknad`() {
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn FantIkkeSøknad.left()
        }

        val saksbehandlingRepo = mock<SøknadsbehandlingRepo>()

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = saksbehandlingRepo,
            søknadService = søknadServiceMock,
        )

        val søknadId = UUID.randomUUID()
        serviceAndMocks.søknadsbehandlingService.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = søknadId,
            ),
        ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.FantIkkeSøknad.left()

        verify(søknadServiceMock).hentSøknad(søknadId)
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil dersom søknad allrede er lukket`() {
        val lukketSøknad = nySakMedjournalførtSøknadOgOppgave().second.lukk(
            lukketTidspunkt = fixedTidspunkt,
            lukketAv = NavIdentBruker.Saksbehandler("sas"),
            type = Søknad.Journalført.MedOppgave.Lukket.LukketType.BORTFALT,
        )

        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn lukketSøknad.right()
        }

        val saksbehandlingRepo = mock<SøknadsbehandlingRepo>()

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = saksbehandlingRepo,
            søknadService = søknadServiceMock,
        )

        serviceAndMocks.søknadsbehandlingService.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = lukketSøknad.id,
            ),
        ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.SøknadErLukket.left()

        verify(søknadServiceMock).hentSøknad(lukketSøknad.id)
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil dersom søknad ikke er journalført med oppgave`() {
        val utenJournalpostOgOppgave = nySakMedNySøknad().second

        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn utenJournalpostOgOppgave.right()
        }

        val saksbehandlingRepo = mock<SøknadsbehandlingRepo>()

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = saksbehandlingRepo,
            søknadService = søknadServiceMock,
        )

        serviceAndMocks.søknadsbehandlingService.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = utenJournalpostOgOppgave.id,
            ),
        ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.SøknadManglerOppgave.left()

        verify(søknadServiceMock).hentSøknad(utenJournalpostOgOppgave.id)
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil dersom søknad har påbegynt behandling`() {
        val (sak, søknadsbehandling) = søknadsbehandlingBeregnetInnvilget()
        val søknad = sak.søknader.first() as Søknad.Journalført.MedOppgave
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>() {
            on { hentForSøknad(any()) } doReturn søknadsbehandling
        }

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            søknadService = søknadServiceMock,
        )

        serviceAndMocks.søknadsbehandlingService.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = søknad.id,
            ),
        ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.SøknadHarAlleredeBehandling.left()

        verify(søknadServiceMock).hentSøknad(søknad.id)
        verify(søknadsbehandlingRepoMock).hentForSøknad(søknad.id)
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil dersom det allerede finnes en åpen søknadsbehandling`() {
        val søknad = nySakMedjournalførtSøknadOgOppgave().second
        val eksisterendeSøknadsbehandling = søknadsbehandlingBeregnetInnvilget().second
        val søknadServiceMock = mock<SøknadService> {
            on { hentSøknad(any()) } doReturn søknad.right()
        }

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentForSak(any(), anyOrNull()) } doReturn listOf(eksisterendeSøknadsbehandling)
        }

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            søknadService = søknadServiceMock,
        )

        serviceAndMocks.søknadsbehandlingService.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = søknad.id,
            ),
        ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.HarAlleredeÅpenSøknadsbehandling.left()

        verify(søknadServiceMock).hentSøknad(søknad.id)
        verify(søknadsbehandlingRepoMock).hentForSøknad(søknad.id)
        verify(søknadsbehandlingRepoMock).defaultSessionContext()
        verify(søknadsbehandlingRepoMock).hentForSak(argThat { it shouldBe søknad.sakId }, anyOrNull())
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Oppretter behandling og publiserer event`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        // Skal kunne starte ny behandling selv om vi har iverksatte behandlinger.
        val eksisterendeSøknadsbehandlinger = listOf(
            søknadsbehandlingIverksattAvslagMedBeregning().second,
            søknadsbehandlingIverksattAvslagUtenBeregning().second,
            søknadsbehandlingIverksattInnvilget().second,
        )
        val søknadService: SøknadService = mock {
            on { hentSøknad(any()) } doReturn søknad.right()
        }

        val capturedSøknadsbehandling = argumentCaptor<NySøknadsbehandling>()
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentForSak(any(), anyOrNull()) } doReturn eksisterendeSøknadsbehandlinger
            doNothing().whenever(mock).lagreNySøknadsbehandling(capturedSøknadsbehandling.capture())
            doAnswer { capturedSøknadsbehandling.firstValue.toSøknadsbehandling(saksnummer) }.whenever(mock).hent(any())
        }

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            søknadService = søknadService,
            avkortingsvarselRepo = mock() {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
        )

        serviceAndMocks.søknadsbehandlingService.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = søknad.id,
            ),
        ).orNull()!!.shouldBeEqualToIgnoringFields(
            Søknadsbehandling.Vilkårsvurdert.Uavklart(
                id = capturedSøknadsbehandling.firstValue.id,
                opprettet = capturedSøknadsbehandling.firstValue.opprettet,
                sakId = søknad.sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = søknad.oppgaveId,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
                fnr = søknad.søknadInnhold.personopplysninger.fnr,
                fritekstTilBrev = "",
                stønadsperiode = null,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
                sakstype = sak.type,
            ),
            // periode er null for Søknadsbehandling.Vilkårsvurdert.Uavklart og vil gi exception dersom man kaller get() på den.
            Søknadsbehandling.Vilkårsvurdert.Uavklart::periode,
        )
        verify(søknadService).hentSøknad(argThat { it shouldBe søknad.id })
        verify(søknadsbehandlingRepoMock).hentForSøknad(argThat { it shouldBe søknad.id })
        verify(søknadsbehandlingRepoMock).defaultSessionContext()
        verify(søknadsbehandlingRepoMock).hentForSak(argThat { it shouldBe søknad.sakId }, anyOrNull())
        verify(serviceAndMocks.avkortingsvarselRepo).hentUtestående(søknad.sakId)

        verify(søknadsbehandlingRepoMock).lagreNySøknadsbehandling(
            argThat {
                it shouldBe NySøknadsbehandling(
                    id = capturedSøknadsbehandling.firstValue.id,
                    opprettet = capturedSøknadsbehandling.firstValue.opprettet,
                    sakId = søknad.sakId,
                    søknad = søknad,
                    oppgaveId = søknad.oppgaveId,
                    fnr = søknad.søknadInnhold.personopplysninger.fnr,
                    avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
                    sakstype = sak.type,
                )
            },
        )

        verify(søknadsbehandlingRepoMock).hent(
            argThat { it shouldBe capturedSøknadsbehandling.firstValue.id },
        )
        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe capturedSøknadsbehandling.firstValue.id })
        verify(serviceAndMocks.observer).handle(
            argThat {
                it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingOpprettet(
                    Søknadsbehandling.Vilkårsvurdert.Uavklart(
                        id = capturedSøknadsbehandling.firstValue.id,
                        opprettet = capturedSøknadsbehandling.firstValue.opprettet,
                        sakId = søknad.sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = søknad.oppgaveId,
                        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
                        fnr = søknad.søknadInnhold.personopplysninger.fnr,
                        fritekstTilBrev = "",
                        stønadsperiode = null,
                        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                        vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
                        attesteringer = Attesteringshistorikk.empty(),
                        avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
                        sakstype = sak.type,
                    ),
                )
            },
        )
        serviceAndMocks.verifyNoMoreInteractions()
    }
}
