package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.søknad.nySakMedLukketSøknad
import no.nav.su.se.bakover.test.søknad.nySakMedNySøknad
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnhold
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.toSøknadsbehandling
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingUføre
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
        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn nySakUføre(fixedClock).first.right()
            },
        ).also {
            it.søknadsbehandlingService.opprett(
                SøknadsbehandlingService.OpprettRequest(
                    søknadId = UUID.randomUUID(),
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.KunneIkkeOppretteSøknadsbehandling(Sak.KunneIkkeOppretteSøknad.FantIkkeSøknad)
                .left()
        }
    }

    @Test
    fun `svarer med feil dersom man oppretter behandling på lukket søknad`() {
        val (sak, lukketSøknad) = nySakMedLukketSøknad()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock { on { hentSak(any<UUID>()) } doReturn sak.right() },
        ).also {
            it.søknadsbehandlingService.opprett(
                SøknadsbehandlingService.OpprettRequest(
                    søknadId = lukketSøknad.id,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.KunneIkkeOppretteSøknadsbehandling(Sak.KunneIkkeOppretteSøknad.ErLukket)
                .left()
        }
    }

    @Test
    fun `svarer med feil dersom søknad ikke er journalført med oppgave`() {
        val (sak, søknad) = nySakMedNySøknad()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        ).also {
            it.søknadsbehandlingService.opprett(
                SøknadsbehandlingService.OpprettRequest(
                    søknadId = søknad.id,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.KunneIkkeOppretteSøknadsbehandling(Sak.KunneIkkeOppretteSøknad.ManglerOppgave)
                .left()
        }
    }

    @Test
    fun `svarer med feil dersom søknad har påbegynt behandling`() {
        val (sak, søknadsbehandling) = søknadsbehandlingIverksattAvslagMedBeregning()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        ).also {
            it.søknadsbehandlingService.opprett(
                SøknadsbehandlingService.OpprettRequest(
                    søknadId = søknadsbehandling.søknad.id,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.KunneIkkeOppretteSøknadsbehandling(Sak.KunneIkkeOppretteSøknad.HarAlleredeBehandling)
                .left()
        }
    }

    @Test
    fun `svarer med feil dersom det allerede finnes en åpen søknadsbehandling`() {
        var (sak, _) = vilkårsvurdertSøknadsbehandlingUføre()

        val nySøknad = nySøknadJournalførtMedOppgave(
            clock = fixedClock,
            sakId = sak.id,
            søknadInnhold = søknadinnhold(
                personopplysninger = Personopplysninger(sak.fnr),
            ),
        )

        sak = sak.copy(søknader = sak.søknader + nySøknad)

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        ).also {
            it.søknadsbehandlingService.opprett(
                SøknadsbehandlingService.OpprettRequest(
                    søknadId = nySøknad.id,
                    sakId = nySøknad.sakId,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeOpprette.KunneIkkeOppretteSøknadsbehandling(Sak.KunneIkkeOppretteSøknad.HarÅpenBehandling)
                .left()
        }
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
            doAnswer { capturedSøknadsbehandling.firstValue.toSøknadsbehandling(saksnummer) }.whenever(mock)
                .hent(any())
        }

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            søknadService = søknadService,
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        )

        serviceAndMocks.søknadsbehandlingService.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = søknad.id,
                sakId = sak.id,
                saksbehandler = saksbehandler,
            ),
        ).getOrFail().shouldBeEqualToIgnoringFields(
            Søknadsbehandling.Vilkårsvurdert.Uavklart(
                id = capturedSøknadsbehandling.firstValue.id,
                opprettet = capturedSøknadsbehandling.firstValue.opprettet,
                sakId = søknad.sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = søknad.oppgaveId,
                fnr = søknad.fnr,
                fritekstTilBrev = "",
                stønadsperiode = null,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
                sakstype = sak.type,
                saksbehandler = saksbehandler,
            ),
            // periode er null for Søknadsbehandling.Vilkårsvurdert.Uavklart og vil gi exception dersom man kaller get() på den.
            Søknadsbehandling.Vilkårsvurdert.Uavklart::periode,
        )
        verify(serviceAndMocks.sakService).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(søknadsbehandlingRepoMock).lagreNySøknadsbehandling(
            argThat {
                it shouldBe NySøknadsbehandling(
                    id = capturedSøknadsbehandling.firstValue.id,
                    opprettet = capturedSøknadsbehandling.firstValue.opprettet,
                    sakId = søknad.sakId,
                    søknad = søknad,
                    oppgaveId = søknad.oppgaveId,
                    fnr = søknad.fnr,
                    avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
                    sakstype = sak.type,
                    saksbehandler = saksbehandler,
                )
            },
        )

        verify(søknadsbehandlingRepoMock).hent(
            argThat { it shouldBe capturedSøknadsbehandling.firstValue.id },
        )
        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe capturedSøknadsbehandling.firstValue.id })
        verify(serviceAndMocks.observer).handle(
            argThat {
                it shouldBe StatistikkEvent.Behandling.Søknad.Opprettet(
                    Søknadsbehandling.Vilkårsvurdert.Uavklart(
                        id = capturedSøknadsbehandling.firstValue.id,
                        opprettet = capturedSøknadsbehandling.firstValue.opprettet,
                        sakId = søknad.sakId,
                        saksnummer = saksnummer,
                        søknad = søknad,
                        oppgaveId = søknad.oppgaveId,
                        fnr = søknad.fnr,
                        fritekstTilBrev = "",
                        stønadsperiode = null,
                        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                        vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
                        attesteringer = Attesteringshistorikk.empty(),
                        avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
                        sakstype = sak.type,
                        saksbehandler = saksbehandler,
                    ),
                    saksbehandler = saksbehandler,
                )
            },
        )
        serviceAndMocks.verifyNoMoreInteractions()
    }
}
