package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.søknadId
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Clock

internal class AvslåSøknadServiceTest {

    @Test
    fun `kan avslå en søknad uten påbegynt behandling`() {
        val (_, uavklart) = søknadsbehandlingVilkårsvurdertUavklart()

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { opprett(any()) } doReturn uavklart.right()
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            clock = fixedClock,
        ).let {
            val response = it.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ).getOrFail("$it")

            val expectedSøknadsbehandling = Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                id = uavklart.id,
                opprettet = uavklart.opprettet,
                sakId = uavklart.sakId,
                saksnummer = uavklart.saksnummer,
                søknad = uavklart.søknad,
                oppgaveId = uavklart.oppgaveId,
                behandlingsinformasjon = uavklart.behandlingsinformasjon,
                fnr = uavklart.fnr,
                saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                attesteringer = Attesteringshistorikk(
                    attesteringer = listOf(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant("saksemannen"),
                            opprettet = Tidspunkt.now(fixedClock),
                        ),
                    ),
                ),
                fritekstTilBrev = "finfin tekst",
                stønadsperiode = uavklart.stønadsperiode!!,
                grunnlagsdata = uavklart.grunnlagsdata,
                vilkårsvurderinger = uavklart.vilkårsvurderinger,
            )

            verify(it.søknadsbehandlingService).hentForSøknad(søknadId)
            verify(it.søknadsbehandlingService).opprett(SøknadsbehandlingService.OpprettRequest(søknadId = søknadId))
            verify(it.søknadsbehandlingService).lagre(argThat { it.søknadsbehandling shouldBe expectedSøknadsbehandling })
            verify(it.vedtakService).lagre(
                argThat {
                    it shouldBe Vedtak.Avslag.AvslagVilkår(
                        id = response.id,
                        opprettet = response.opprettet,
                        behandling = expectedSøknadsbehandling,
                        saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                        attestant = NavIdentBruker.Attestant("saksemannen"),
                        periode = expectedSøknadsbehandling.periode,
                        avslagsgrunner = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON)
                    )
                },
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan avslå en søknad med påbegynt behandling`() {
        val (_, vilkårsvurdertInnvilget) = søknadsbehandlingVilkårsvurdertInnvilget()

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(søknadId) } doReturn vilkårsvurdertInnvilget
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            clock = fixedClock,
        ).let {
            val response = it.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ).getOrFail("$it")

            val expectedSøknadsbehandling = Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                id = vilkårsvurdertInnvilget.id,
                opprettet = vilkårsvurdertInnvilget.opprettet,
                sakId = vilkårsvurdertInnvilget.sakId,
                saksnummer = vilkårsvurdertInnvilget.saksnummer,
                søknad = vilkårsvurdertInnvilget.søknad,
                oppgaveId = vilkårsvurdertInnvilget.oppgaveId,
                behandlingsinformasjon = vilkårsvurdertInnvilget.behandlingsinformasjon,
                fnr = vilkårsvurdertInnvilget.fnr,
                saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                attesteringer = Attesteringshistorikk(
                    attesteringer = listOf(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant("saksemannen"),
                            opprettet = Tidspunkt.now(fixedClock),
                        ),
                    ),
                ),
                fritekstTilBrev = "finfin tekst",
                stønadsperiode = vilkårsvurdertInnvilget.stønadsperiode,
                grunnlagsdata = vilkårsvurdertInnvilget.grunnlagsdata,
                vilkårsvurderinger = vilkårsvurdertInnvilget.vilkårsvurderinger,
            )

            verify(it.søknadsbehandlingService).hentForSøknad(søknadId)
            verify(it.søknadsbehandlingService).lagre(argThat { it.søknadsbehandling shouldBe expectedSøknadsbehandling })
            verify(it.vedtakService).lagre(
                argThat {
                    it shouldBe Vedtak.Avslag.AvslagVilkår(
                        id = response.id,
                        opprettet = response.opprettet,
                        behandling = expectedSøknadsbehandling,
                        saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                        attestant = NavIdentBruker.Attestant("saksemannen"),
                        periode = expectedSøknadsbehandling.periode,
                        avslagsgrunner = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON)
                    )
                },
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom ugyldig tilstand`() {
        val (_, iverksatt) = søknadsbehandlingIverksattInnvilget()

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(søknadId) } doReturn iverksatt
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            clock = fixedClock,
        ).let {
            it.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ) shouldBe KunneIkkeAvslåSøknad.SøknadsbehandlingIUgyldigTilstandForAvslag.left()
        }
    }

    @Test
    fun `svarer med feil dersom vi ikke får opprettet behandling`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { opprett(any()) } doReturn SøknadsbehandlingService.KunneIkkeOpprette.SøknadHarAlleredeBehandling.left()
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            clock = fixedClock,
        ).let {
            it.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ) shouldBe KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.left()
        }
    }

    private data class AvslåSøknadServiceAndMocks(
        val clock: Clock = fixedClock,
        val søknadService: SøknadService = mock(),
        val søknadsbehandlingService: SøknadsbehandlingService = mock(),
        val vedtakService: VedtakService = mock(),
        val sessionFactory: SessionFactory = TestSessionFactory(),
    ) {
        val service = AvslåSøknadService(
            clock = clock,
            søknadsbehandlingService = søknadsbehandlingService,
            vedtakService = vedtakService,
        )

        fun mocks(): Array<Any> {
            return listOf(
                søknadService,
                søknadsbehandlingService,
                vedtakService,
            ).toTypedArray()
        }

        fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(
                søknadService,
                søknadsbehandlingService,
                vedtakService,
            )
        }
    }
}
