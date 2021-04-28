package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.fixedLocalDate
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class OpprettRevurderingServiceTest {

    private val sakId: UUID = UUID.randomUUID()

    private val denFørsteInneværendeMåned = fixedLocalDate.let {
        LocalDate.of(
            it.year,
            it.month,
            1,
        )
    }
    private val nesteMåned =
        LocalDate.of(
            denFørsteInneværendeMåned.year,
            denFørsteInneværendeMåned.month.plus(1),
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
    private val stønadsperiode = ValgtStønadsperiode(
        periode = periode,
        begrunnelse = "begrunnelse",
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
        on { periode } doReturn periode
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
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.EMPTY,
        vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
    )

    private fun createSak() = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = fixedTidspunkt,
        fnr = fnr,
        søknader = listOf(),
        behandlinger = listOf(createInnvilgetBehandling()),
        utbetalinger = createUtbetalinger(),
        vedtakListe = listOf(Vedtak.fromSøknadsbehandling(createInnvilgetBehandling(), UUID30.randomUUID())),
    )

    private fun createUtbetalinger(): List<Utbetaling> = listOf(
        mock {
            on { senesteDato() } doReturn periode.tilOgMed
            on { tidligsteDato() } doReturn periode.fraOgMed
        },
    )

    @Test
    fun `oppretter en revurdering`() {
        val sak = createSak()
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService> {
            on { opprettVilkårsvurderinger(any(), any()) } doReturn Vilkårsvurderinger.EMPTY
        }

        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        ).orNull()!!

        val tilRevurdering = sak.vedtakListe.first() as Vedtak.EndringIYtelse
        actual shouldBe OpprettetRevurdering(
            id = actual.id,
            periode = periode,
            opprettet = actual.opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )
        inOrder(
            sakServiceMock,
            personServiceMock,
            oppgaveServiceMock,
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
        ) {
            verify(sakServiceMock).hentSak(sakId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(vilkårsvurderingServiceMock).opprettVilkårsvurderinger(sakId, periode)
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
            verify(vilkårsvurderingServiceMock).lagre(argThat { it shouldBe actual.id }, argThat { it shouldBe Vilkårsvurderinger(uføre = Vilkår.IkkeVurdert.Uførhet) })
        }

        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppretter ikke en revurdering hvis perioden er i samme måned`() {
        val mocks = RevurderingServiceMocks()
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = 1.januar(2021),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.KanIkkeRevurdereInneværendeMånedEllerTidligere.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke sak`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn FantIkkeSak.left()
        }
        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )
        actual shouldBe KunneIkkeOppretteRevurdering.FantIkkeSak.left()
        verify(sakServiceMock).hentSak(sakId)
        mocks.verifyNoMoreInteractions()
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
        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes.left()
        verify(sakServiceMock).hentSak(sakId)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan ikke revurdere når stønadsperioden ikke inneholder revurderingsperioden`() {

        val beregningMock = mock<Beregning> {
            on { periode } doReturn Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.desember(2021))
        }
        val behandling = mock<Søknadsbehandling.Iverksatt.Innvilget> {
            on { beregning } doReturn beregningMock
        }
        val sak = Sak(
            id = sakId,
            saksnummer = saksnummer,
            opprettet = fixedTidspunkt,
            fnr = fnr,
            søknader = listOf(),
            behandlinger = listOf(behandling),
            utbetalinger = createUtbetalinger(),
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes.left()
        verify(sakServiceMock).hentSak(sakId)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `for en ny revurdering vil det tas utgangspunkt i nyeste vedtak hvor fraOgMed er inni perioden`() {
        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn FnrGenerator.random()
            on { saksnummer } doReturn Saksnummer(1337)
        }
        val vedtakForFørsteJanuarLagetNå = mock<Vedtak.EndringIYtelse> {
            on { opprettet } doReturn fixedTidspunkt
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
            on { behandling } doReturn behandlingMock
            on { behandlingsinformasjon } doReturn Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                .withAlleVilkårOppfylt()
        }
        val vedtakForFørsteMarsLagetNå = mock<Vedtak.EndringIYtelse> {
            on { opprettet } doReturn fixedTidspunkt.plus(1, ChronoUnit.SECONDS)
            on { periode } doReturn Periode.create(1.mars(2021), 31.desember(2021))
            on { behandling } doReturn behandlingMock
            on { behandlingsinformasjon } doReturn Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                .withAlleVilkårOppfylt()
        }
        val vedtakForFørsteJanuarLagetForLengeSiden = mock<Vedtak.EndringIYtelse> {
            on { opprettet } doReturn fixedTidspunkt.instant.minus(2, ChronoUnit.HALF_DAYS).toTidspunkt()
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
            on { behandling } doReturn behandlingMock
            on { behandlingsinformasjon } doReturn Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                .withAlleVilkårOppfylt()
        }

        val sak = Sak(
            id = sakId,
            saksnummer = saksnummer,
            opprettet = fixedTidspunkt,
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

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService> {
            on { opprettVilkårsvurderinger(any(), any()) } doReturn Vilkårsvurderinger.EMPTY
        }

        val mocks = RevurderingServiceMocks(
            // clock = Clock.fixed(1.januar(2020).startOfDay(zoneIdOslo).instant, zoneIdOslo),
            sakService = sakServiceMock,
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn oppgaveId.right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            vilkårsvurderingService = vilkårsvurderingServiceMock,

        )
        val revurderingForFebruar = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = fixedLocalDate.plus(1, ChronoUnit.MONTHS),
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        revurderingForFebruar shouldBeRight {
            it.tilRevurdering shouldBe vedtakForFørsteJanuarLagetNå
        }

        val revurderingForApril = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = fixedLocalDate.plus(3, ChronoUnit.MONTHS),
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
                forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                behandlingsinformasjon = opprinneligVedtak.behandlingsinformasjon,
                grunnlagsdata = Grunnlagsdata.EMPTY,
                vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
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
        val revurderingRepoMock = mock<RevurderingRepo>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn oppgaveId.right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService> {
            on { opprettVilkårsvurderinger(any(), any()) } doReturn Vilkårsvurderinger.EMPTY
        }
        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
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
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual })
        verify(vilkårsvurderingServiceMock).opprettVilkårsvurderinger(sakId, periode)
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Revurderingsbehandling(
                    saksnummer = saksnummer,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                )
            },
        )
        verify(vilkårsvurderingServiceMock).lagre(any(), any())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig periode`() {
        val sak = createSak()

        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                // tester at fraOgMed må starte på 1.
                fraOgMed = periode.tilOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
            .left()
        verify(sakServiceMock).hentSak(sakId)
        mocks.verifyNoMoreInteractions()
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

        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
            personService = personServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIkkeAktørId.left()
        verify(sakServiceMock).hentSak(sakId)
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        mocks.verifyNoMoreInteractions()
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

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService> {
            on { opprettVilkårsvurderinger(any(), any()) } doReturn Vilkårsvurderinger.EMPTY
        }

        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
            ),
        )
        actual shouldBe KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave.left()
        verify(sakServiceMock).hentSak(sakId)
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        verify(vilkårsvurderingServiceMock).opprettVilkårsvurderinger(sakId, periode)
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.Revurderingsbehandling(
                    saksnummer = saksnummer,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }
}
