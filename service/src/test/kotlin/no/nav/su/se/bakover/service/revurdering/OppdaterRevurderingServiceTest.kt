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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.fixedLocalDate
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.formueVilkår
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.sakId
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.søknadsbehandlingVedtak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.vilkårsvurderinger
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.create
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

val oppgaveId = OppgaveId("oppgaveId")

internal class OppdaterRevurderingServiceTest {
    private val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()

    private val opprettetFraOgMed = fixedLocalDate
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
    private val saksbehandler = NavIdentBruker.Saksbehandler("Sak S. behandler")
    private val revurderingId = UUID.randomUUID()

    private val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )

    private val informasjonSomRevurderes = listOf(
        Revurderingsteg.Uførhet,
    )

    private val uføregrunnlag = Grunnlag.Uføregrunnlag(
        periode = periode,
        uføregrad = Uføregrad.parse(25),
        forventetInntekt = 12000,
    )

    private val vilkårsvurderingUføre = Vilkår.Uførhet.Vurdert.create(
        vurderingsperioder = nonEmptyListOf(
            Vurderingsperiode.Uføre.create(
                resultat = Resultat.Innvilget,
                grunnlag = uføregrunnlag,
                periode = periode,
                begrunnelse = "ok",
                opprettet = fixedTidspunkt
            ),
        ),
    )

    private val tilRevurdering = søknadsbehandlingVedtak.copy(periode = periode)
    private val opprettetRevurdering = OpprettetRevurdering(
        id = revurderingId,
        periode = periode,
        opprettet = fixedTidspunkt,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = null,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = Grunnlagsdata(
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periode,
                    begrunnelse = null,
                ),
            ),
        ),
        vilkårsvurderinger = Vilkårsvurderinger(
            uføre = vilkårsvurderingUføre,
        ),
        informasjonSomRevurderes = InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert)),
    )

    @Test
    fun `ugyldig begrunnelse`() {
        val mocks = RevurderingServiceMocks()
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = opprettetFraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig årsak`() {
        val mocks = RevurderingServiceMocks()
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = opprettetFraOgMed,
                årsak = "UGYLDIG_ÅRSAK",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigÅrsak.left()
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `ugyldig periode`() {
        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden).left()
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = opprettetFraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )

        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden).left()
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(any(), any())
        verify(revurderingRepoMock).hent(any())
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Fant ikke revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = opprettetFraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.FantIkkeRevurdering.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Kan ikke oppdatere sendt forhåndsvarslet revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.copy(
                forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt(
                    journalpostId = JournalpostId("journalpostId"),
                    brevbestillingId = BrevbestillingId("brevbestillingId"),
                ),
            )
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed.plus(1, ChronoUnit.DAYS),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Kan ikke oppdatere besluttet forhåndsvarslet revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.copy(
                forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                    journalpostId = JournalpostId("journalpostId"),
                    brevbestillingId = BrevbestillingId("brevbestillingId"),
                    begrunnelse = "besluttetForhåndsvarslingBegrunnelse",
                    valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                ),
            )
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed.plus(1, ChronoUnit.DAYS),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Oppdatering av iverksatt revurdering gir ugyldig tilstand`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(tilRevurdering),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.let {
                IverksattRevurdering.Innvilget(
                    id = it.id,
                    periode = it.periode,
                    opprettet = it.opprettet,
                    tilRevurdering = it.tilRevurdering,
                    saksbehandler = it.saksbehandler,
                    oppgaveId = it.oppgaveId,
                    fritekstTilBrev = it.fritekstTilBrev,
                    revurderingsårsak = it.revurderingsårsak,
                    beregning = mock(),
                    attestering = Attestering.Iverksatt(
                        attestant = NavIdentBruker.Attestant("navIdent"),
                    ),
                    behandlingsinformasjon = behandlingsinformasjon,
                    simulering = mock(),
                    forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
                    grunnlagsdata = Grunnlagsdata(
                        bosituasjon = listOf(
                            Grunnlag.Bosituasjon.Fullstendig.Enslig(
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                periode = it.periode,
                                begrunnelse = null,
                            ),
                        ),
                    ),
                    vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
                    informasjonSomRevurderes = it.informasjonSomRevurderes,
                )
            }
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigTilstand(
            IverksattRevurdering.Innvilget::class,
            OpprettetRevurdering::class,
        ).left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(opprettetRevurdering.sakId, opprettetRevurdering.periode.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppdater en revurdering`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(tilRevurdering),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).getOrHandle {
            throw RuntimeException("$it")
        }

        actual.let { oppdatertRevurdering ->
            oppdatertRevurdering.periode shouldBe periode
            oppdatertRevurdering.tilRevurdering shouldBe tilRevurdering
            oppdatertRevurdering.saksbehandler shouldBe saksbehandler
            oppdatertRevurdering.oppgaveId shouldBe OppgaveId("oppgaveId")
            oppdatertRevurdering.fritekstTilBrev shouldBe ""
            oppdatertRevurdering.revurderingsårsak shouldBe revurderingsårsak
            oppdatertRevurdering.forhåndsvarsel shouldBe null
            oppdatertRevurdering.behandlingsinformasjon shouldBe tilRevurdering.behandlingsinformasjon
            oppdatertRevurdering.vilkårsvurderinger.uføre.grunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(tilRevurdering.behandling.vilkårsvurderinger.uføre.grunnlag.first())
            }
            oppdatertRevurdering.vilkårsvurderinger.uføre.ekvivalentMed(vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert)
            oppdatertRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Uførhet to Vurderingstatus.IkkeVurdert))
        }

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
            grunnlagServiceMock,
            vedtakServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
            verify(revurderingRepoMock).lagre(actual)
            verify(vilkårsvurderingServiceMock).lagre(actual.id, actual.vilkårsvurderinger)
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(actual.id, actual.grunnlagsdata.fradragsgrunnlag)
            verify(grunnlagServiceMock).lagreBosituasjongrunnlag(actual.id, actual.grunnlagsdata.bosituasjon)
        }
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `må velge minst ting som skal revurderes`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = emptyList(),
            ),
        ) shouldBe KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes.left()
    }

    @Test
    fun `grunnlag resettes dersom man oppdaterer revurderingen`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(tilRevurdering),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()
        val grunnlagServiceMock = mock<GrunnlagService>()

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering.copy(
                // simuler at det er gjort endringer før oppdatering
                grunnlagsdata = Grunnlagsdata(),
                vilkårsvurderinger = Vilkårsvurderinger(
                    uføre = Vilkår.Uførhet.IkkeVurdert,
                ),
            )
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        )

        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed,
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "g-regulering",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ),
        ).getOrHandle { throw RuntimeException("$it") }

        actual.periode.fraOgMed shouldBe periode.fraOgMed
        actual.revurderingsårsak.årsak shouldBe Revurderingsårsak.Årsak.REGULER_GRUNNBELØP
        actual.revurderingsårsak.begrunnelse.toString() shouldBe "g-regulering"

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
            vedtakServiceMock,
            grunnlagServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(opprettetRevurdering.sakId, opprettetRevurdering.periode.fraOgMed)
            verify(revurderingRepoMock).lagre(actual)
            verify(vilkårsvurderingServiceMock).lagre(actual.id, actual.vilkårsvurderinger)
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(actual.id, actual.grunnlagsdata.fradragsgrunnlag)
            verify(grunnlagServiceMock).lagreBosituasjongrunnlag(actual.id, actual.grunnlagsdata.bosituasjon)
        }
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `støtter ikke tilfeller hvor gjeldende vedtaksdata ikke er sammenhengende i tid`() {
        val førsteVedtak = søknadsbehandlingVedtak
        val periodePlussEtÅr = periode.copy(
            periode.fraOgMed.plusYears(1),
            periode.tilOgMed.plusYears(1),
        )
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periodePlussEtÅr,
            uføregrad = Uføregrad.parse(25),
            forventetInntekt = 12000,
        )
        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            begrunnelse = null,
        )
        val uførevilkår = Vilkår.Uførhet.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(
                Vurderingsperiode.Uføre.create(
                    resultat = Resultat.Innvilget,
                    grunnlag = uføregrunnlag,
                    periode = periodePlussEtÅr,
                    begrunnelse = "ok",
                    opprettet = fixedTidspunkt
                ),
            ),
        )
        val andreVedtakFormueVilkår = formueVilkår(periodePlussEtÅr)
        val andreVedtak = søknadsbehandlingVedtak.copy(
            periode = periodePlussEtÅr,
            behandling = (søknadsbehandlingVedtak.behandling as Søknadsbehandling.Iverksatt.Innvilget).copy(
                stønadsperiode = Stønadsperiode.create(periodePlussEtÅr),
                grunnlagsdata = Grunnlagsdata(bosituasjon = listOf(bosituasjon)),
                vilkårsvurderinger = Vilkårsvurderinger(
                    uføre = uførevilkår,
                    formue = andreVedtakFormueVilkår,
                ),
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
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = opprettetRevurdering.id,
                fraOgMed = opprettetRevurdering.periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Test",
                saksbehandler = opprettetRevurdering.saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.TidslinjeForVedtakErIkkeKontinuerlig.left()
        verify(revurderingRepoMock).hent(opprettetRevurdering.id)
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `revurdering med flere bosituasjonsperioder må vurderes`() {
        fun lagBeregning(periode: Periode) = mock<Beregning> {
            on { getFradrag() } doReturn listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            )
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val revurderingsperiode = Periode.create(
            fraOgMed = periode.fraOgMed.plus(1, ChronoUnit.MONTHS),
            tilOgMed = periode.tilOgMed,
        )
        val revurderingBeregning = lagBeregning(revurderingsperiode)

        val revurdering = mock<IverksattRevurdering.Innvilget> {
            on { attestering } doReturn Attestering.Iverksatt(NavIdentBruker.Attestant("attestantSomIverksatte"))
            on { behandlingsinformasjon } doReturn mock()

            on { periode } doReturn revurderingsperiode
            on { beregning } doReturn revurderingBeregning
            on { simulering } doReturn mock()
            on { saksbehandler } doReturn mock()
            on { grunnlagsdata } doReturn Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = periode,
                        begrunnelse = null,
                    ),
                ),
            )
            on { vilkårsvurderinger } doReturn Vilkårsvurderinger(
                uføre = vilkårsvurderingUføre,
            )
        }

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(
                søknadsbehandlingVedtak.copy(
                    beregning = lagBeregning(periode),
                ),
                Vedtak.from(revurdering, UUID30.randomUUID()),
            ),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        )

        actual shouldBe KunneIkkeOppdatereRevurdering.BosituasjonMedFlerePerioderMåRevurderes.left()
        verify(revurderingRepoMock).hent(revurderingId)
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `revurdering med EPS inntekt og flere bosituasjoner må vurderes`() {
        fun lagBeregning(periode: Periode) = mock<Beregning> {
            on { getFradrag() } doReturn listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            )
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }

        val revurderingsperiode = Periode.create(
            fraOgMed = periode.fraOgMed.plus(1, ChronoUnit.MONTHS),
            tilOgMed = periode.tilOgMed,
        )
        val revurderingBeregning = lagBeregning(revurderingsperiode)

        val revurdering = mock<IverksattRevurdering.Innvilget> {
            on { attestering } doReturn Attestering.Iverksatt(NavIdentBruker.Attestant("attestantSomIverksatte"))
            on { behandlingsinformasjon } doReturn mock()

            on { periode } doReturn revurderingsperiode
            on { beregning } doReturn revurderingBeregning
            on { simulering } doReturn mock()
            on { saksbehandler } doReturn mock()
            on { grunnlagsdata } doReturn Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = periode,
                        begrunnelse = null,
                    ),
                ),
            )
            on { vilkårsvurderinger } doReturn Vilkårsvurderinger(
                uføre = vilkårsvurderingUføre,
            )
        }

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periode,
            vedtakListe = nonEmptyListOf(
                søknadsbehandlingVedtak.copy(
                    beregning = lagBeregning(periode),
                ),
                Vedtak.from(revurdering, UUID30.randomUUID()),
            ),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Bosituasjon),
            ),
        )

        actual shouldBe KunneIkkeOppdatereRevurdering.EpsInntektMedFlereBosituasjonsperioderMåRevurderes.left()
        verify(revurderingRepoMock).hent(revurderingId)
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periode.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }
}
