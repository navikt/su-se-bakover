package no.nav.su.se.bakover.service.søknad

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.søknadId
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class AvslåSøknadManglendeDokumentasjonServiceImplTest {
    @Test
    fun `kan avslå en søknad uten påbegynt behandling`() {
        val (sak, uavklart) = søknadsbehandlingVilkårsvurdertUavklart(stønadsperiode = null)

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { opprett(any()) } doReturn uavklart.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val dokument = Dokument.UtenMetadata.Vedtak(
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = "",
        )

        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn dokument.right()
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            oppgaveService = oppgaveServiceMock,
            brevService = brevServiceMock,
            sakService = sakServiceMock,
            clock = fixedClock,
        ).let { serviceAndMocks ->
            serviceAndMocks.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ).getOrFail("$serviceAndMocks")

            val expectedPeriode = Periode.create(
                fraOgMed = LocalDate.now(serviceAndMocks.clock).startOfMonth(),
                tilOgMed = LocalDate.now(serviceAndMocks.clock).endOfMonth(),
            )

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
                attesteringer = Attesteringshistorikk.create(
                    attesteringer = listOf(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant("saksemannen"),
                            opprettet = Tidspunkt.now(fixedClock),
                        ),
                    ),
                ),
                fritekstTilBrev = "finfin tekst",
                stønadsperiode = Stønadsperiode.create(
                    periode = expectedPeriode,
                ),
                grunnlagsdata = uavklart.grunnlagsdata,
                vilkårsvurderinger = uavklart.vilkårsvurderinger.leggTil(
                    OpplysningspliktVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodeOpplysningsplikt.create(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(fixedClock),
                                grunnlag = Opplysningspliktgrunnlag(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(fixedClock),
                                    periode = expectedPeriode,
                                    beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                                ),
                                periode = expectedPeriode,
                            ),
                        ),
                    ).getOrFail(),
                ),
                avkorting = AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                    håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
                ),
                sakstype = sak.type,
            )

            val expectedAvslagVilkår = Avslagsvedtak.AvslagVilkår(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                behandling = expectedSøknadsbehandling,
                saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                attestant = NavIdentBruker.Attestant("saksemannen"),
                periode = expectedPeriode,
                avslagsgrunner = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON),
            )

            verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(søknadId)
            verify(serviceAndMocks.søknadsbehandlingService).opprett(SøknadsbehandlingService.OpprettRequest(søknadId = søknadId))
            verify(serviceAndMocks.søknadsbehandlingService).lagre(
                argThat {
                    it.søknadsbehandling.shouldBeEqualToIgnoringFields(
                        expectedSøknadsbehandling,
                        Søknadsbehandling::vilkårsvurderinger,
                    )

                    it.søknadsbehandling.vilkårsvurderinger.erLik(expectedSøknadsbehandling.vilkårsvurderinger)
                },
                argThat { TestSessionFactory.transactionContext },
            )

            val actualVedtak = argumentCaptor<Vedtak>()
            verify(serviceAndMocks.vedtakService).lagre(
                actualVedtak.capture(),
                argThat { TestSessionFactory.transactionContext },
            ).also {
                actualVedtak.firstValue.shouldBeEqualToIgnoringFields(
                    expectedAvslagVilkår,
                    VedtakSomKanRevurderes::id,
                )
            }
            verify(serviceAndMocks.oppgaveService).lukkOppgave(expectedSøknadsbehandling.oppgaveId)
            verify(serviceAndMocks.brevService).lagDokument(
                argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe actualVedtak.firstValue },
            )
            verify(serviceAndMocks.brevService).lagreDokument(
                argThat {
                    it shouldBe dokument.leggTilMetadata(
                        metadata = Dokument.Metadata(
                            sakId = expectedSøknadsbehandling.sakId,
                            søknadId = expectedSøknadsbehandling.søknad.id,
                            vedtakId = actualVedtak.firstValue.id,
                            bestillBrev = true,
                        ),
                    )
                },
                argThat { TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.sakService).hentSak(expectedSøknadsbehandling.sakId)
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan avslå en søknad med påbegynt behandling`() {
        val (sak, vilkårsvurdertInnvilget) = søknadsbehandlingVilkårsvurdertInnvilget()

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(søknadId) } doReturn vilkårsvurdertInnvilget
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val dokument = Dokument.UtenMetadata.Vedtak(
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = "",
        )

        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn dokument.right()
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            oppgaveService = oppgaveServiceMock,
            brevService = brevServiceMock,
            sakService = sakServiceMock,
            clock = fixedClock,
        ).let { serviceAndMocks ->
            serviceAndMocks.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ).getOrFail("$serviceAndMocks")

            val expectedPeriode = Periode.create(
                fraOgMed = LocalDate.now(serviceAndMocks.clock).startOfMonth(),
                tilOgMed = LocalDate.now(serviceAndMocks.clock).endOfMonth(),
            )

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
                attesteringer = Attesteringshistorikk.create(
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
                vilkårsvurderinger = vilkårsvurdertInnvilget.vilkårsvurderinger.leggTil(
                    OpplysningspliktVilkår.Vurdert.tryCreate(
                        vurderingsperioder = nonEmptyListOf(
                            VurderingsperiodeOpplysningsplikt.create(
                                id = UUID.randomUUID(),
                                opprettet = Tidspunkt.now(fixedClock),
                                grunnlag = Opplysningspliktgrunnlag(
                                    id = UUID.randomUUID(),
                                    opprettet = Tidspunkt.now(fixedClock),
                                    periode = expectedPeriode,
                                    beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                                ),
                                periode = expectedPeriode,
                            ),
                        ),
                    ).getOrFail(),
                ),
                avkorting = AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                    håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
                ),
                sakstype = sak.type,
            )

            val expectedAvslagVilkår = Avslagsvedtak.AvslagVilkår(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                behandling = expectedSøknadsbehandling,
                saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                attestant = NavIdentBruker.Attestant("saksemannen"),
                periode = expectedSøknadsbehandling.periode,
                avslagsgrunner = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON),
            )

            verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(søknadId)
            verify(serviceAndMocks.søknadsbehandlingService).lagre(
                argThat {
                    it.søknadsbehandling.shouldBeEqualToIgnoringFields(
                        expectedSøknadsbehandling,
                        Søknadsbehandling::vilkårsvurderinger,
                    )
                    it.søknadsbehandling.vilkårsvurderinger.erLik(expectedSøknadsbehandling.vilkårsvurderinger)
                },
                argThat { TestSessionFactory.transactionContext },
            )
            val actualVedtak = argumentCaptor<Vedtak>()
            verify(serviceAndMocks.vedtakService).lagre(
                actualVedtak.capture(),
                argThat { TestSessionFactory.transactionContext },
            ).also {
                actualVedtak.firstValue.shouldBeEqualToIgnoringFields(
                    expectedAvslagVilkår,
                    VedtakSomKanRevurderes::id,
                )
            }
            verify(serviceAndMocks.oppgaveService).lukkOppgave(expectedSøknadsbehandling.oppgaveId)
            verify(serviceAndMocks.brevService).lagDokument(
                argThat<Visitable<LagBrevRequestVisitor>> {
                    it.shouldBeEqualToIgnoringFields(
                        expectedAvslagVilkår,
                        VedtakSomKanRevurderes::id,
                    )
                },
            )
            verify(serviceAndMocks.brevService).lagreDokument(
                argThat {
                    it shouldBe dokument.leggTilMetadata(
                        metadata = Dokument.Metadata(
                            sakId = expectedSøknadsbehandling.sakId,
                            søknadId = expectedSøknadsbehandling.søknad.id,
                            vedtakId = actualVedtak.firstValue.id,
                            bestillBrev = true,
                        ),
                    )
                },
                argThat { TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.sakService).hentSak(expectedSøknadsbehandling.sakId)
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom ugyldig tilstand`() {
        val (_, iverksatt) = søknadsbehandlingIverksattInnvilget()

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { hentForSøknad(søknadId) } doReturn iverksatt
        }

        assertThrows<IllegalArgumentException> {
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
                )

                verify(søknadsbehandlingServiceMock).hentForSøknad(søknadId)
                it.verifyNoMoreInteractions()
            }
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
            ) shouldBe KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.SøknadHarAlleredeBehandling.left()

            verify(søknadsbehandlingServiceMock).hentForSøknad(any())
            verify(søknadsbehandlingServiceMock).opprett(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `overlever dersom lukking av oppgave feiler`() {
        val (sak, uavklart) = søknadsbehandlingVilkårsvurdertUavklart()

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { opprett(any()) } doReturn uavklart.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn OppgaveFeil.KunneIkkeLukkeOppgave.left()
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val dokument = Dokument.UtenMetadata.Vedtak(
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = "",
        )

        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn dokument.right()
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            oppgaveService = oppgaveServiceMock,
            brevService = brevServiceMock,
            sakService = sakServiceMock,
            clock = fixedClock,
        ).let {
            it.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ).getOrFail("Feil i testdataoppsett")

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
            verify(it.brevService).lagDokument(any<Visitable<LagBrevRequestVisitor>>())
            verify(it.brevService).lagreDokument(
                any(),
                argThat { TestSessionFactory.transactionContext },
            )
            verify(it.oppgaveService).lukkOppgave(uavklart.oppgaveId)
            verify(it.sakService).hentSak(sak.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom opprettesle av dokument feiler`() {
        val (_, uavklart) = søknadsbehandlingVilkårsvurdertUavklart()

        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { opprett(any()) } doReturn uavklart.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
        }

        AvslåSøknadServiceAndMocks(
            søknadsbehandlingService = søknadsbehandlingServiceMock,
            oppgaveService = oppgaveServiceMock,
            brevService = brevServiceMock,
            clock = fixedClock,
        ).let { serviceAndMocks ->
            serviceAndMocks.service.avslå(
                AvslåManglendeDokumentasjonRequest(
                    søknadId,
                    saksbehandler = NavIdentBruker.Saksbehandler("saksemannen"),
                    fritekstTilBrev = "finfin tekst",
                ),
            ) shouldBe KunneIkkeAvslåSøknad.KunneIkkeGenererePDF.left()

            verify(serviceAndMocks.søknadsbehandlingService).hentForSøknad(søknadId)
            verify(serviceAndMocks.søknadsbehandlingService).opprett(SøknadsbehandlingService.OpprettRequest(søknadId = søknadId))
            verify(serviceAndMocks.brevService).lagDokument(any<Visitable<LagBrevRequestVisitor>>())
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    private data class AvslåSøknadServiceAndMocks(
        val clock: Clock = fixedClock,
        val søknadService: SøknadService = mock(),
        val søknadsbehandlingService: SøknadsbehandlingService = mock(),
        val vedtakService: VedtakService = mock(),
        val oppgaveService: OppgaveService = mock(),
        val brevService: BrevService = mock(),
        val sakService: SakService = mock(),
        val sessionFactory: SessionFactory = TestSessionFactory(),
        val satsFactory: SatsFactory = satsFactoryTest,
    ) {
        val service = AvslåSøknadManglendeDokumentasjonServiceImpl(
            clock = clock,
            søknadsbehandlingService = søknadsbehandlingService,
            vedtakService = vedtakService,
            oppgaveService = oppgaveService,
            brevService = brevService,
            sessionFactory = sessionFactory,
            sakService = sakService,
            satsFactory = satsFactory,
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
