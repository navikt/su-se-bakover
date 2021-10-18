package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
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
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock

internal class AvslåSøknadManglendeDokumentasjonServiceImplTest {
    @Test
    fun `kan avslå en søknad uten påbegynt behandling`() {
        val (_, uavklart) = søknadsbehandlingVilkårsvurdertUavklart()

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { opprett(any()) } doReturn uavklart.right()
        }
        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val dokument = Dokument.UtenMetadata.Vedtak(
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = "",
        )

        val brevServiceMock = mock<BrevService>() {
            on { lagDokument(any()) } doReturn dokument.right()
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            oppgaveService = oppgaveServiceMock,
            brevService = brevServiceMock,
            clock = fixedClock,
        ).let { serviceAndMocks ->
            val response = serviceAndMocks.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ).getOrFail("$serviceAndMocks")

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

            val expectedAvslagVilkår = Vedtak.Avslag.AvslagVilkår(
                id = response.id,
                opprettet = response.opprettet,
                behandling = expectedSøknadsbehandling,
                saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                attestant = NavIdentBruker.Attestant("saksemannen"),
                periode = expectedSøknadsbehandling.periode,
                avslagsgrunner = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON),
            )

            verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(søknadId)
            verify(serviceAndMocks.søknadsbehandlingService).opprett(SøknadsbehandlingService.OpprettRequest(søknadId = søknadId))
            verify(serviceAndMocks.søknadsbehandlingService).lagre(
                argThat { it.søknadsbehandling shouldBe expectedSøknadsbehandling },
                argThat { TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.vedtakService).lagre(
                argThat { it shouldBe expectedAvslagVilkår },
                argThat { TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.oppgaveService).lukkOppgave(expectedSøknadsbehandling.oppgaveId)
            verify(serviceAndMocks.brevService).lagDokument(argThat { it shouldBe expectedAvslagVilkår })
            verify(serviceAndMocks.brevService).lagreDokument(
                argThat {
                    it shouldBe dokument.leggTilMetadata(
                        metadata = Dokument.Metadata(
                            sakId = expectedSøknadsbehandling.sakId,
                            søknadId = expectedSøknadsbehandling.søknad.id,
                            vedtakId = expectedAvslagVilkår.id,
                            bestillBrev = true,
                        ),
                    )
                },
                argThat { TestSessionFactory.transactionContext },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan avslå en søknad med påbegynt behandling`() {
        val (_, vilkårsvurdertInnvilget) = søknadsbehandlingVilkårsvurdertInnvilget()

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(søknadId) } doReturn vilkårsvurdertInnvilget
        }
        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val dokument = Dokument.UtenMetadata.Vedtak(
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = "",
        )

        val brevServiceMock = mock<BrevService>() {
            on { lagDokument(any()) } doReturn dokument.right()
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            oppgaveService = oppgaveServiceMock,
            brevService = brevServiceMock,
            clock = fixedClock,
        ).let { serviceAndMocks ->
            val response = serviceAndMocks.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ).getOrFail("$serviceAndMocks")

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

            val expectedAvslagVilkår = Vedtak.Avslag.AvslagVilkår(
                id = response.id,
                opprettet = response.opprettet,
                behandling = expectedSøknadsbehandling,
                saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                attestant = NavIdentBruker.Attestant("saksemannen"),
                periode = expectedSøknadsbehandling.periode,
                avslagsgrunner = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON),
            )

            verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(søknadId)
            verify(serviceAndMocks.søknadsbehandlingService).lagre(
                argThat { it.søknadsbehandling shouldBe expectedSøknadsbehandling },
                argThat { TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.vedtakService).lagre(
                argThat { it shouldBe expectedAvslagVilkår },
                argThat { TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.oppgaveService).lukkOppgave(expectedSøknadsbehandling.oppgaveId)
            verify(serviceAndMocks.brevService).lagDokument(argThat { it shouldBe expectedAvslagVilkår })
            verify(serviceAndMocks.brevService).lagreDokument(
                argThat {
                    it shouldBe dokument.leggTilMetadata(
                        metadata = Dokument.Metadata(
                            sakId = expectedSøknadsbehandling.sakId,
                            søknadId = expectedSøknadsbehandling.søknad.id,
                            vedtakId = expectedAvslagVilkår.id,
                            bestillBrev = true,
                        ),
                    )
                },
                argThat { TestSessionFactory.transactionContext },
            )
            serviceAndMocks.verifyNoMoreInteractions()
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

            verify(søknadsbehandlingServiceMock).hentForSøknad(søknadId)
            it.verifyNoMoreInteractions()
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

            verify(søknadsbehandlingServiceMock).hentForSøknad(any())
            verify(søknadsbehandlingServiceMock).opprett(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `overlever dersom lukking av oppgave feiler`() {
        val (_, uavklart) = søknadsbehandlingVilkårsvurdertUavklart()

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { opprett(any()) } doReturn uavklart.right()
        }
        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgave(any()) } doReturn OppgaveFeil.KunneIkkeLukkeOppgave.left()
        }

        val dokument = Dokument.UtenMetadata.Vedtak(
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = "",
        )

        val brevServiceMock = mock<BrevService>() {
            on { lagDokument(any()) } doReturn dokument.right()
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            oppgaveService = oppgaveServiceMock,
            brevService = brevServiceMock,
            clock = fixedClock,
        ).let {
            val response = it.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ).getOrFail("Feil i testdataoppsett")

            response shouldBe beOfType<Vedtak.Avslag.AvslagVilkår>()

            verify(it.søknadsbehandlingService).hentForSøknad(any())
            verify(it.søknadsbehandlingService).opprett(any())
            verify(it.søknadsbehandlingService).lagre(
                any(),
                argThat { TestSessionFactory.transactionContext },
            )
            verify(it.vedtakService).lagre(
                any(),
                argThat { TestSessionFactory.transactionContext },
            )
            verify(it.brevService).lagDokument(any())
            verify(it.brevService).lagreDokument(
                any(),
                argThat { TestSessionFactory.transactionContext },
            )
            verify(it.oppgaveService).lukkOppgave(uavklart.oppgaveId)
            it.verifyNoMoreInteractions()
        }
    }

    private data class AvslåSøknadServiceAndMocks(
        val clock: Clock = fixedClock,
        val søknadService: SøknadService = mock(),
        val søknadsbehandlingService: SøknadsbehandlingService = mock(),
        val vedtakService: VedtakService = mock(),
        val oppgaveService: OppgaveService = mock(),
        val brevService: BrevService = mock(),
        val sessionFactory: SessionFactory = TestSessionFactory(),
    ) {
        val service = AvslåSøknadManglendeDokumentasjonServiceImpl(
            clock = clock,
            søknadsbehandlingService = søknadsbehandlingService,
            vedtakService = vedtakService,
            oppgaveService = oppgaveService,
            brevService = brevService,
            sessionFactory = sessionFactory,
        )

        fun verifyNoMoreInteractions() {
            verifyNoMoreInteractions(
                søknadService,
                søknadsbehandlingService,
                vedtakService,
                oppgaveService,
                brevService,
            )
        }
    }
}
