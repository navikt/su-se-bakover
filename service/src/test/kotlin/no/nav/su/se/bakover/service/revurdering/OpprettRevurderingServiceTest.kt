package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.fixedLocalDate
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknadsbehandling.testBeregning
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
    private val stønadsperiode = Stønadsperiode.create(
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

    private val informasjonSomRevurderes = listOf(
        Revurderingsteg.Inntekt,
    )

    private val uføregrunnlag = Grunnlag.Uføregrunnlag(
        periode = stønadsperiode.periode,
        uføregrad = Uføregrad.parse(25),
        forventetInntekt = 12000,
    )

    private val vilkårsvurderingUføre = Vilkår.Vurdert.Uførhet.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlag,
                periode = stønadsperiode.periode,
                begrunnelse = "ok",
            ),
        ),
    )

    private fun createBeregningMock() = mock<Beregning> {
        on { periode } doReturn periode
        on { getMånedsberegninger() } doReturn periode.tilMånedsperioder()
            .map { MånedsberegningFactory.ny(it, Sats.HØY, listOf()) }
        on { getFradrag() } doReturn listOf()
        on { getSumYtelse() } doReturn periode.tilMånedsperioder()
            .sumOf { MånedsberegningFactory.ny(it, Sats.HØY, listOf()).getSumYtelse() }
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
        grunnlagsdata = Grunnlagsdata(
            uføregrunnlag = listOf(uføregrunnlag),
        ),
        vilkårsvurderinger = Vilkårsvurderinger(
            uføre = vilkårsvurderingUføre,
        ),
    )

    private fun createSøknadsbehandlingVedtak() = Vedtak.fromSøknadsbehandling(createInnvilgetBehandling(), UUID30.randomUUID())

    @Test
    fun `oppretter en revurdering`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(createSøknadsbehandlingVedtak()),
        )

        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        val grunnlagServiceMock = mock<GrunnlagService>()

        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            grunnlagService = grunnlagServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).orNull()!!

        val tilRevurdering = gjeldendeVedtaksdata.gjeldendePeriodeTilOriginaltVedtak.values.first() as Vedtak.EndringIYtelse
        actual.let { opprettetRevurdering ->
            opprettetRevurdering.periode shouldBe periode
            opprettetRevurdering.tilRevurdering shouldBe tilRevurdering
            opprettetRevurdering.saksbehandler shouldBe saksbehandler
            opprettetRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
            opprettetRevurdering.fritekstTilBrev shouldBe ""
            opprettetRevurdering.revurderingsårsak shouldBe Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MELDING_FRA_BRUKER.toString(), begrunnelse = "Ny informasjon",
            )
            opprettetRevurdering.forhåndsvarsel shouldBe null
            opprettetRevurdering.behandlingsinformasjon shouldBe tilRevurdering.behandlingsinformasjon
            opprettetRevurdering.grunnlagsdata.uføregrunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(uføregrunnlag)
            }
            opprettetRevurdering.vilkårsvurderinger.uføre.ekvivalentMed(vilkårsvurderingUføre)
            opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert))

            inOrder(
                sakServiceMock,
                personServiceMock,
                oppgaveServiceMock,
                revurderingRepoMock,
                vilkårsvurderingServiceMock,
                grunnlagServiceMock,
            ) {
                verify(sakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
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
                verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual.right() })
                verify(vilkårsvurderingServiceMock).lagre(actual.id, actual.vilkårsvurderinger)
                verify(grunnlagServiceMock).lagreFradragsgrunnlag(actual.id, actual.grunnlagsdata.fradragsgrunnlag)
            }

            mocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kan opprette revurdering med årsak g-regulering i samme måned`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(createSøknadsbehandlingVedtak()),
        )

        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()
        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            grunnlagService = grunnlagServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "g-regulering",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).getOrHandle {
            throw RuntimeException("$it")
        }

        val tilRevurdering = gjeldendeVedtaksdata.gjeldendePeriodeTilOriginaltVedtak.values.first() as Vedtak.EndringIYtelse
        val periode = Periode.create(periode.fraOgMed, periode.tilOgMed)
        actual.let { opprettetRevurdering ->
            opprettetRevurdering.periode shouldBe periode
            opprettetRevurdering.tilRevurdering shouldBe tilRevurdering
            opprettetRevurdering.saksbehandler shouldBe saksbehandler
            opprettetRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
            opprettetRevurdering.fritekstTilBrev shouldBe ""
            opprettetRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
                Revurderingsårsak.Begrunnelse.create("g-regulering"),
            )
            opprettetRevurdering.forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel
            opprettetRevurdering.behandlingsinformasjon shouldBe tilRevurdering.behandlingsinformasjon
            opprettetRevurdering.grunnlagsdata.uføregrunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(uføregrunnlag.copy(periode = periode))
            }
            opprettetRevurdering.vilkårsvurderinger.uføre.ekvivalentMed(
                vilkårsvurderingUføre.copy(
                    vurderingsperioder = vilkårsvurderingUføre.vurderingsperioder.map {
                        it.copy(CopyArgs.Tidslinje.NyPeriode(periode))
                    },
                ),
            )
            opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert))
        }
        inOrder(
            sakServiceMock,
            personServiceMock,
            oppgaveServiceMock,
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
            grunnlagServiceMock,
        ) {
            verify(sakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
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
            verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual.right() })
            verify(vilkårsvurderingServiceMock).lagre(any(), any())
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(any(), any())
        }

        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan opprette revurdering med årsak g-regulering 1 kalendermåned tilbake i tid`() {
        val periode = Periode.create(periode.tilOgMed.minusMonths(1).startOfMonth(), periode.tilOgMed)

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(createSøknadsbehandlingVedtak()),
        )

        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()
        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            grunnlagService = grunnlagServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.tilOgMed.minusMonths(1).startOfMonth(),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "g-regulering",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).getOrHandle {
            throw RuntimeException("$it")
        }
        val tilRevurdering = gjeldendeVedtaksdata.gjeldendePeriodeTilOriginaltVedtak.values.first() as Vedtak.EndringIYtelse

        actual.let { opprettetRevurdering ->
            opprettetRevurdering.periode shouldBe periode
            opprettetRevurdering.tilRevurdering shouldBe tilRevurdering
            opprettetRevurdering.saksbehandler shouldBe saksbehandler
            opprettetRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
            opprettetRevurdering.fritekstTilBrev shouldBe ""
            opprettetRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
                Revurderingsårsak.Begrunnelse.create("g-regulering"),
            )
            opprettetRevurdering.forhåndsvarsel shouldBe Forhåndsvarsel.IngenForhåndsvarsel
            opprettetRevurdering.behandlingsinformasjon shouldBe tilRevurdering.behandlingsinformasjon
            opprettetRevurdering.grunnlagsdata.uføregrunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(uføregrunnlag.copy(periode = periode))
            }
            opprettetRevurdering.vilkårsvurderinger.uføre.ekvivalentMed(
                vilkårsvurderingUføre.copy(
                    vurderingsperioder = vilkårsvurderingUføre.vurderingsperioder.map {
                        it.copy(CopyArgs.Tidslinje.NyPeriode(periode))
                    },
                ),
            )
            opprettetRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert))
        }

        inOrder(
            sakServiceMock,
            personServiceMock,
            oppgaveServiceMock,
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
            grunnlagServiceMock,
        ) {
            verify(sakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
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
            verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual.right() })
            verify(vilkårsvurderingServiceMock).lagre(any(), any())
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(any(), any())
        }

        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke sak`() {
        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn KunneIkkeKopiereGjeldendeVedtaksdata.FantIkkeSak.left()
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
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        )
        actual shouldBe KunneIkkeOppretteRevurdering.FantIkkeSak.left()
        verify(sakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan ikke opprette revurdering hvis ingen vedtak eksisterer for angitt fra og med dato`() {
        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak.left()
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
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes.left()
        verify(sakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `for en ny revurdering vil det tas utgangspunkt i nyeste vedtak hvor fraOgMed er inni perioden`() {
        val behandlingMock = mock<Behandling> {
            on { fnr } doReturn FnrGenerator.random()
            on { saksnummer } doReturn Saksnummer(2021)
            on { grunnlagsdata } doReturn Grunnlagsdata(
                uføregrunnlag = listOf(uføregrunnlag),
            )
            on { vilkårsvurderinger } doReturn Vilkårsvurderinger(
                uføre = vilkårsvurderingUføre,
            )
        }
        val vedtakForFørsteJanuarLagetNå = mock<Vedtak.EndringIYtelse> {
            on { id } doReturn UUID.randomUUID()
            on { opprettet } doReturn fixedTidspunkt
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
            on { behandling } doReturn behandlingMock
            on { behandlingsinformasjon } doReturn Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                .withAlleVilkårOppfylt()
            on { beregning } doReturn testBeregning
        }
        val vedtakForFørsteMarsLagetNå = mock<Vedtak.EndringIYtelse> {
            on { id } doReturn UUID.randomUUID()
            on { opprettet } doReturn fixedTidspunkt.plus(1, ChronoUnit.SECONDS)
            on { periode } doReturn Periode.create(1.mars(2021), 31.desember(2021))
            on { behandling } doReturn behandlingMock
            on { behandlingsinformasjon } doReturn Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                .withAlleVilkårOppfylt()
            on { beregning } doReturn testBeregning
        }
        val vedtakForFørsteJanuarLagetForLengeSiden = mock<Vedtak.EndringIYtelse> {
            on { id } doReturn UUID.randomUUID()
            on { opprettet } doReturn fixedTidspunkt.instant.minus(2, ChronoUnit.HALF_DAYS).toTidspunkt()
            on { periode } doReturn Periode.create(1.januar(2021), 31.desember(2021))
            on { behandling } doReturn behandlingMock
            on { behandlingsinformasjon } doReturn Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                .withAlleVilkårOppfylt()
            on { beregning } doReturn testBeregning
        }

        val fraOgMedDatoFebruar = fixedLocalDate.plus(1, ChronoUnit.MONTHS)
        val fraOgMedDatoApril = fixedLocalDate.plus(3, ChronoUnit.MONTHS)

        val vedtakListe = nonEmptyListOf(
            vedtakForFørsteJanuarLagetNå,
            vedtakForFørsteMarsLagetNå,
            vedtakForFørsteJanuarLagetForLengeSiden,
        )

        val gjeldendeVedtaksdataFebruar = GjeldendeVedtaksdata(
            periode = Periode.create(fraOgMedDatoFebruar, vedtakListe.maxOf { it.periode.tilOgMed }),
            vedtakListe = vedtakListe,
        )

        val gjeldendeVedtaksdataApril = GjeldendeVedtaksdata(
            periode = Periode.create(fraOgMedDatoApril, vedtakListe.maxOf { it.periode.tilOgMed }),
            vedtakListe = vedtakListe,
        )

        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(sakId, fraOgMedDatoFebruar) } doReturn gjeldendeVedtaksdataFebruar.right()
            on { kopierGjeldendeVedtaksdata(sakId, fraOgMedDatoApril) } doReturn gjeldendeVedtaksdataApril.right()
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
        )
        val revurderingForFebruar = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = fraOgMedDatoFebruar,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )

        revurderingForFebruar shouldBeRight {
            it.tilRevurdering shouldBe vedtakForFørsteJanuarLagetNå
        }

        val revurderingForApril = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = fraOgMedDatoApril,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes,
            ),
        )

        revurderingForApril shouldBeRight {
            it.tilRevurdering shouldBe vedtakForFørsteMarsLagetNå
        }
    }

    @Test
    fun `kan revurdere en periode med eksisterende revurdering`() {
        val opprinneligVedtak = Vedtak.fromSøknadsbehandling(createInnvilgetBehandling(), UUID30.randomUUID())
        val revurdering = Vedtak.from(
            IverksattRevurdering.Innvilget(
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
                grunnlagsdata = Grunnlagsdata(
                    uføregrunnlag = listOf(uføregrunnlag),
                ),
                vilkårsvurderinger = Vilkårsvurderinger(
                    uføre = vilkårsvurderingUføre,
                ),
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            ),
            UUID30.randomUUID(),
        )

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(
                opprinneligVedtak,
                revurdering,
            ),
        )

        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn oppgaveId.right()
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()
        val mocks = RevurderingServiceMocks(
            sakService = sakServiceMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            grunnlagService = grunnlagServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )

        actual shouldBeRight {
            it.saksnummer shouldBe saksnummer
            it.tilRevurdering.id shouldBe revurdering.id
        }

        verify(sakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        verify(revurderingRepoMock).lagre(argThat { it.right() shouldBe actual })
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
        verify(grunnlagServiceMock).lagreFradragsgrunnlag(any(), any())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig periode`() {
        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden).left()
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
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden).left()
        verify(sakServiceMock).kopierGjeldendeVedtaksdata(any(), any())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fant ikke aktør id`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(createSøknadsbehandlingVedtak()),
        )

        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
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
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.FantIkkeAktørId.left()
        verify(sakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
        verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(createSøknadsbehandlingVedtak()),
        )

        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

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
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave.left()
        verify(sakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
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
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `må velge noe som skal revurderes`() {
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val mocks = RevurderingServiceMocks(
            personService = personServiceMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = emptyList(),
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `støtter ikke tilfeller hvor gjeldende vedtaksdata ikke er sammenhengende i tid`() {
        val førsteVedtak = createSøknadsbehandlingVedtak()
        val periodePlussEtÅr = periode.copy(
            periode.fraOgMed.plusYears(1),
            periode.tilOgMed.plusYears(1),
        )
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periodePlussEtÅr,
            uføregrad = Uføregrad.parse(25),
            forventetInntekt = 12000,
        )
        val uførevilkår = Vilkår.Vurdert.Uførhet.create(
            vurderingsperioder = nonEmptyListOf(
                Vurderingsperiode.Uføre.create(
                    resultat = Resultat.Innvilget,
                    grunnlag = uføregrunnlag,
                    periode = periodePlussEtÅr,
                    begrunnelse = "ok",
                ),
            ),
        )
        val andreVedtak = createSøknadsbehandlingVedtak().copy(
            periode = periodePlussEtÅr,
            behandling = createInnvilgetBehandling().copy(
                grunnlagsdata = Grunnlagsdata(
                    uføregrunnlag = listOf(uføregrunnlag),
                ),
                vilkårsvurderinger = Vilkårsvurderinger(uføre = uførevilkår),
            ),
        )

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode.copy(
                førsteVedtak.periode.fraOgMed,
                andreVedtak.periode.tilOgMed,
            ),
            vedtakListe = nonEmptyListOf(
                førsteVedtak,
                andreVedtak,
            ),
        )

        val sakServiceMock = mock<SakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
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
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppretteRevurdering.TidslinjeForVedtakErIkkeKontinuerlig.left()
        verify(sakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }
}
