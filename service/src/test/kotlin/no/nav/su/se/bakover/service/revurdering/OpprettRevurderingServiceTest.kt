package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class OpprettRevurderingServiceTest {

    private val sakId: UUID = UUID.randomUUID()

    private val dagensDato = LocalDate.now().let {
        LocalDate.of(
            it.year,
            it.month,
            1,
        )
    }
    private val nesteMåned =
        LocalDate.of(
            dagensDato.year,
            dagensDato.month.plus(1),
            1,
        )
    private val periode = Periode.create(
        fraOgMed = nesteMåned,
        tilOgMed = nesteMåned.let {
            val treMånederFramITid = it.plusMonths(3)
            LocalDate.of(
                treMånederFramITid.year,
                treMånederFramITid.month,
                treMånederFramITid.lengthOfMonth(),
            )
        },
    )
    private val saksbehandler = NavIdentBruker.Saksbehandler("Sak S. behandler")
    private val saksnummer = Saksnummer(nummer = 12345676)
    private val fnr = FnrGenerator.random()
    private val aktørId = AktørId("aktørId")

    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )

    private fun createBeregningMock() = mock<Beregning> {
        on { getPeriode() } doReturn periode
        on { getMånedsberegninger() } doReturn periode.tilMånedsperioder()
            .map { MånedsberegningFactory.ny(it, Sats.HØY, listOf()) }
        on { getFradrag() } doReturn listOf()
        on { getSumYtelse() } doReturn periode.tilMånedsperioder()
            .sumBy { MånedsberegningFactory.ny(it, Sats.HØY, listOf()).getSumYtelse() }
    }

    private fun createInnvilgetBehandling() = Søknadsbehandling.Iverksatt.Innvilget(
        id = mock(),
        opprettet = mock(),
        sakId = sakId,
        saksnummer = saksnummer,
        søknad = mock(),
        oppgaveId = mock(),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().copy(
            bosituasjon = Behandlingsinformasjon.Bosituasjon(
                ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                delerBolig = false,
                ektemakeEllerSamboerUførFlyktning = null,
                begrunnelse = null,
            ),
        ),
        fnr = fnr,
        beregning = createBeregningMock(),
        simulering = mock(),
        saksbehandler = saksbehandler,
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("Attes T. Ant")),
        fritekstTilBrev = "",
        grunnlagsdata = Grunnlagsdata.EMPTY,
    )

    private fun createSak() = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        søknader = listOf(),
        behandlinger = listOf(createInnvilgetBehandling()),
        utbetalinger = createUtbetalinger(),
        vedtakListe = listOf(Vedtak.fromSøknadsbehandling(createInnvilgetBehandling(), UUID30.randomUUID())),
    )

    private fun createUtbetalinger(): List<Utbetaling> = listOf(
        mock {
            on { senesteDato() } doReturn periode.getTilOgMed()
            on { tidligsteDato() } doReturn periode.getFraOgMed()
        },
    )

    @Test
    fun `oppretter en revurdering`() {
        val sak = createSak()
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { lagre(any()) }.doNothing()
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val grunnlagServiceMock = mock<GrunnlagService> {
            on { opprettGrunnlag(any(), any()) } doReturn Grunnlagsdata.EMPTY
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            grunnlagService = grunnlagServiceMock,
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.getFraOgMed(),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        ).orNull()!!

        actual shouldBe OpprettetRevurdering(
            id = actual.id,
            periode = periode,
            opprettet = actual.opprettet,
            tilRevurdering = sak.vedtakListe.first() as Vedtak.EndringIYtelse,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )
        inOrder(
            sakServiceMock,
            personServiceMock,
            oppgaveServiceMock,
            revurderingRepoMock,
            grunnlagServiceMock,
        ) {
            verify(sakServiceMock).hentSak(sakId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(grunnlagServiceMock).opprettGrunnlag(sakId, periode)
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = null,
                    )
                },
            )
            verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual.right() })
        }

        verifyNoMoreInteractions(sakServiceMock, personServiceMock, oppgaveServiceMock, revurderingRepoMock)
    }

    @Test
    fun `oppretter ikke en revurdering hvis perioden er i samme måned`() {
        val actual = createRevurderingService().opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = 1.januar(2021),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.KanIkkeRevurdereInneværendeMånedEllerTidligere.left()
    }

    @Test
    fun `fant ikke sak`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn FantIkkeSak.left()
        }
        val actual = createRevurderingService(
            sakService = sakServiceMock,
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.getFraOgMed(),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )
        actual shouldBe KunneIkkeOppretteRevurdering.FantIkkeSak.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `kan ikke revurdere når ingen behandlinger er IVERKSATT_INNVILGET`() {
        val sak = createSak().copy(
            behandlinger = emptyList(),
            vedtakListe = emptyList(),
        )
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.getFraOgMed(),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `kan ikke revurdere når stønadsperioden ikke inneholder revurderingsperioden`() {

        val beregningMock = mock<Beregning> {
            on { getPeriode() } doReturn Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.desember(2021))
        }
        val behandling = mock<Søknadsbehandling.Iverksatt.Innvilget> {
            on { beregning } doReturn beregningMock
        }
        val sak = Sak(
            id = sakId,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(),
            fnr = fnr,
            søknader = listOf(),
            behandlinger = listOf(behandling),
            utbetalinger = createUtbetalinger(),
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.getFraOgMed(),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes.left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `for en ny revurdering vil det tas utgangspunkt i nyeste vedtak hvor fraOgMed er inni perioden`() {
        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn FnrGenerator.random()
            on { saksnummer } doReturn Saksnummer(1337)
        }
        val vedtakForFørsteJanuarLagetNå = mock<Vedtak.EndringIYtelse> {
            on { opprettet } doReturn Tidspunkt.now()
            on { periode } doReturn Periode.create(1.januar(2020), 31.desember(2020))
            on { behandling } doReturn behandlingMock
        }
        val vedtakForFørsteMarsLagetNå = mock<Vedtak.EndringIYtelse> {
            on { opprettet } doReturn Tidspunkt.now()
            on { periode } doReturn Periode.create(1.mars(2020), 31.desember(2020))
            on { behandling } doReturn behandlingMock
        }
        val vedtakForFørsteJanuarLagetForLengeSiden = mock<Vedtak.EndringIYtelse> {
            on { opprettet } doReturn Tidspunkt.now().instant.minus(2, ChronoUnit.HALF_DAYS).toTidspunkt()
            on { periode } doReturn Periode.create(1.januar(2020), 31.desember(2020))
            on { behandling } doReturn behandlingMock
        }

        val sak = Sak(
            id = sakId,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(),
            fnr = fnr,
            søknader = listOf(),
            behandlinger = emptyList(),
            utbetalinger = createUtbetalinger(),
            vedtakListe = listOf(
                vedtakForFørsteJanuarLagetNå,
                vedtakForFørsteMarsLagetNå,
                vedtakForFørsteJanuarLagetForLengeSiden,
            ),
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val grunnlagServiceMock = mock<GrunnlagService> {
            on { opprettGrunnlag(any(), any()) } doReturn Grunnlagsdata.EMPTY
        }

        val revurderingForFebruar = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.januar(2020).startOfDay(zoneIdOslo).instant, zoneIdOslo),
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgav1").right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn AktørId("aktør1").right()
            },
            grunnlagService = grunnlagServiceMock,

        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = 1.februar(2020),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        revurderingForFebruar shouldBeRight {
            it.tilRevurdering shouldBe vedtakForFørsteJanuarLagetNå
        }

        val revurderingForApril = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.januar(2020).startOfDay(zoneIdOslo).instant, zoneIdOslo),
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgav1").right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn AktørId("aktør1").right()
            },
            grunnlagService = grunnlagServiceMock,
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = 1.april(2020),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        revurderingForApril shouldBeRight {
            it.tilRevurdering shouldBe vedtakForFørsteMarsLagetNå
        }
    }

    @Test
    fun `kan revurdere en periode med eksisterende revurdering`() {
        val sak = createSak().let {
            val opprinneligVedtak = it.vedtakListe.first() as Vedtak.EndringIYtelse
            val revurdering = IverksattRevurdering.Innvilget(
                id = UUID.randomUUID(),
                periode = periode,
                opprettet = Tidspunkt.EPOCH,
                tilRevurdering = opprinneligVedtak,
                saksbehandler = saksbehandler,
                oppgaveId = OppgaveId("null"),
                beregning = opprinneligVedtak.beregning,
                simulering = opprinneligVedtak.simulering,
                attestering = Attestering.Iverksatt(opprinneligVedtak.attestant),
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = Grunnlagsdata.EMPTY,
            )
            it.copy(
                revurderinger = listOf(
                    revurdering,
                ),
                vedtakListe = it.vedtakListe.plus(
                    Vedtak.from(revurdering, opprinneligVedtak.utbetalingId),
                ),
            )
        }

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.februar(2021).startOfDay(zoneIdOslo).instant, zoneIdOslo),
            personService = mock {
                on { hentAktørId(any()) } doReturn AktørId("aktør1").right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgav1").right()
            },
            grunnlagService = mock {
                on { opprettGrunnlag(any(), any()) } doReturn Grunnlagsdata.EMPTY
            }
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = 1.juni(2021),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBeRight {
            it.saksnummer shouldBe sak.saksnummer
            it.tilRevurdering.id shouldBe sak.vedtakListe.last().id
        }

        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `ugyldig periode`() {
        val sak = createSak()

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.februar(2021).startOfDay(zoneIdOslo).instant, zoneIdOslo),
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                // tester at fraOgMed må starte på 1.
                fraOgMed = 2.juni(2021),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
            .left()
        verify(sakServiceMock).hentSak(sakId)
        verifyNoMoreInteractions(sakServiceMock)
    }

    @Test
    fun `fant ikke aktør id`() {
        val sak = createSak()

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.februar(2021).startOfDay(zoneIdOslo).instant, zoneIdOslo),
            personService = personServiceMock,
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = 1.juni(2021),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIkkeAktørId.left()
        verify(sakServiceMock).hentSak(sakId)
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        val sak = createSak()

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }

        val actual = createRevurderingService(
            sakService = sakServiceMock,
            clock = Clock.fixed(1.februar(2021).startOfDay(zoneIdOslo).instant, zoneIdOslo),
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = 1.juni(2021),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )
        actual shouldBe KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave.left()
        verify(sakServiceMock).hentSak(sakId)
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Revurderingsbehandling(
                    saksnummer = saksnummer,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                )
            },
        )
        verifyNoMoreInteractions(sakServiceMock, personServiceMock)
    }

    private fun createRevurderingService(
        sakService: SakService = mock(),
        utbetalingService: UtbetalingService = mock(),
        revurderingRepo: RevurderingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        microsoftGraphApiClient: MicrosoftGraphApiClient = mock(),
        brevService: BrevService = mock(),
        vedtakRepo: VedtakRepo = mock(),
        clock: Clock = fixedClock,
        grunnlagService: GrunnlagService = mock()
    ) =
        RevurderingServiceImpl(
            sakService = sakService,
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            microsoftGraphApiClient = microsoftGraphApiClient,
            brevService = brevService,
            vedtakRepo = vedtakRepo,
            clock = clock,
            grunnlagService = grunnlagService,
        )
}
