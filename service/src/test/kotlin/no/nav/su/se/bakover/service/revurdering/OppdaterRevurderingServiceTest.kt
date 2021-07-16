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
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
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
import no.nav.su.se.bakover.service.formueVilkår
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.søknadsbehandlingsvedtakIverksattInnvilget
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.behandlingsinformasjonAlleVilkårInnvilget
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.innvilgetUførevilkårForventetInntekt12000
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class OppdaterRevurderingServiceTest {

    private val tilRevurderingInnvilget =
        søknadsbehandlingsvedtakIverksattInnvilget.copy(periode = periodeNesteMånedOgTreMånederFram)

    // Merk at søknadsbehandlingens uførevilkår ikke er likt som OpprettetRevurdering før vi kaller oppdater-funksjonen, men vi forventer at de er like etter oppdateringa.
    private val vilkårsvurderingUføre =
        innvilgetUførevilkårForventetInntekt12000(periode = periodeNesteMånedOgTreMånederFram)

    // TODO jah: Vi burde ha en domeneklasse/factory som oppretter en revurdering fra et Vedtak, så slipper vi å gjøre disse antagelsene i testene
    private val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
        stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
        revurderingsperiode = periodeNesteMånedOgTreMånederFram,
        tilRevurdering = tilRevurderingInnvilget,
    ).copy(
        grunnlagsdata = Grunnlagsdata(
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periodeNesteMånedOgTreMånederFram,
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
                fraOgMed = fixedLocalDate,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
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
                fraOgMed = fixedLocalDate,
                årsak = "UGYLDIG_ÅRSAK",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
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
                fraOgMed = fixedLocalDate,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
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
                fraOgMed = fixedLocalDate,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
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
                forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
            )
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.DAYS),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
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
                    valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                    begrunnelse = "besluttetForhåndsvarslingBegrunnelse",
                ),
            )
        }
        val mocks = RevurderingServiceMocks(revurderingRepo = revurderingRepoMock)
        val actual = mocks.revurderingService.oppdaterRevurdering(
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.DAYS),
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        )
        actual shouldBe KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet.left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `Oppdatering av iverksatt revurdering gir ugyldig tilstand`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(tilRevurderingInnvilget),
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
                    attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant("navIdent"),
                            opprettet = fixedTidspunkt
                        )
                    ),
                    behandlingsinformasjon = behandlingsinformasjonAlleVilkårInnvilget,
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
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "gyldig begrunnelse",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
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
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(tilRevurderingInnvilget),
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
        val oppdatertPeriode = Periode.create(
            periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.MONTHS),
            periodeNesteMånedOgTreMånederFram.tilOgMed,
        )
        val actual = mocks.revurderingService.oppdaterRevurdering(
            // Bruker andre verdier enn den opprinnelige revurderingen for å se at de faktisk forandrer seg
            OppdaterRevurderingRequest(
                revurderingId = revurderingId,
                fraOgMed = oppdatertPeriode.fraOgMed,
                årsak = "ANDRE_KILDER",
                begrunnelse = "bør bli oppdatert",
                saksbehandler = NavIdentBruker.Saksbehandler("En ny saksbehandlinger"),
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        ).getOrHandle {
            fail("$it")
        }

        actual.let { oppdatertRevurdering ->
            oppdatertRevurdering.periode shouldBe periodeNesteMånedOgTreMånederFram
            oppdatertRevurdering.tilRevurdering shouldBe tilRevurderingInnvilget
            oppdatertRevurdering.saksbehandler shouldBe saksbehandler
            oppdatertRevurdering.oppgaveId shouldBe oppgaveIdRevurdering
            oppdatertRevurdering.fritekstTilBrev shouldBe ""
            oppdatertRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                årsak = Revurderingsårsak.Årsak.ANDRE_KILDER,
                begrunnelse = Revurderingsårsak.Begrunnelse.create("bør bli oppdatert"),
            )
            oppdatertRevurdering.forhåndsvarsel shouldBe null
            oppdatertRevurdering.behandlingsinformasjon shouldBe tilRevurderingInnvilget.behandlingsinformasjon
            oppdatertRevurdering.vilkårsvurderinger.uføre.grunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(tilRevurderingInnvilget.behandling.vilkårsvurderinger.uføre.grunnlag.first())
            }
            oppdatertRevurdering.vilkårsvurderinger.uføre.ekvivalentMed(tilRevurderingInnvilget.behandling.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert)
            oppdatertRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert))
        }

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
            grunnlagServiceMock,
            vedtakServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
                sakId = argThat { it shouldBe sakId },
                fraOgMed = argThat {
                    it shouldBe oppdatertPeriode.fraOgMed
                },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
            verify(vilkårsvurderingServiceMock).lagre(
                behandlingId = argThat { it shouldBe actual.id },
                vilkårsvurderinger = argThat { it shouldBe actual.vilkårsvurderinger },
            )
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(
                behandlingId = argThat { it shouldBe actual.id },
                fradragsgrunnlag = argThat { it shouldBe actual.grunnlagsdata.fradragsgrunnlag },
            )
            verify(grunnlagServiceMock).lagreBosituasjongrunnlag(
                behandlingId = argThat { it shouldBe actual.id },
                bosituasjongrunnlag = argThat { actual.grunnlagsdata.bosituasjon },
            )
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
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
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
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(tilRevurderingInnvilget),
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
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "REGULER_GRUNNBELØP",
                begrunnelse = "g-regulering",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Uførhet),
            ),
        ).getOrHandle { fail("$it") }

        actual.periode.fraOgMed shouldBe periodeNesteMånedOgTreMånederFram.fraOgMed
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
        val førsteVedtak = søknadsbehandlingsvedtakIverksattInnvilget
        val periodePlussEtÅr = periodeNesteMånedOgTreMånederFram.copy(
            periodeNesteMånedOgTreMånederFram.fraOgMed.plusYears(1),
            periodeNesteMånedOgTreMånederFram.tilOgMed.plusYears(1),
        )
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periodePlussEtÅr,
            uføregrad = Uføregrad.parse(25),
            forventetInntekt = 12000,
            opprettet = fixedTidspunkt,
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
        val andreVedtak = søknadsbehandlingsvedtakIverksattInnvilget.copy(
            periode = periodePlussEtÅr,
            behandling = (søknadsbehandlingsvedtakIverksattInnvilget.behandling as Søknadsbehandling.Iverksatt.Innvilget).copy(
                stønadsperiode = Stønadsperiode.create(periodePlussEtÅr),
                grunnlagsdata = Grunnlagsdata(bosituasjon = listOf(bosituasjon)),
                vilkårsvurderinger = Vilkårsvurderinger(
                    uføre = uførevilkår,
                    formue = andreVedtakFormueVilkår,
                ),
            ),
        )

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram.copy(
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
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
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
            fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.MONTHS),
            tilOgMed = periodeNesteMånedOgTreMånederFram.tilOgMed,
        )
        val revurderingBeregning = lagBeregning(revurderingsperiode)

        val revurdering = mock<IverksattRevurdering.Innvilget> {
            on { attestering } doReturn Attestering.Iverksatt(NavIdentBruker.Attestant("attestantSomIverksatte"), fixedTidspunkt)
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
                        periode = periodeNesteMånedOgTreMånederFram,
                        begrunnelse = null,
                    ),
                ),
            )
            on { vilkårsvurderinger } doReturn Vilkårsvurderinger(
                uføre = vilkårsvurderingUføre,
            )
        }

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(
                søknadsbehandlingsvedtakIverksattInnvilget.copy(
                    beregning = lagBeregning(periodeNesteMånedOgTreMånederFram),
                ),
                Vedtak.from(revurdering, UUID30.randomUUID(), fixedClock),
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
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Inntekt),
            ),
        )

        actual shouldBe KunneIkkeOppdatereRevurdering.BosituasjonMedFlerePerioderMåRevurderes.left()
        verify(revurderingRepoMock).hent(revurderingId)
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
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
            fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed.plus(1, ChronoUnit.MONTHS),
            tilOgMed = periodeNesteMånedOgTreMånederFram.tilOgMed,
        )
        val revurderingBeregning = lagBeregning(revurderingsperiode)

        val revurdering = mock<IverksattRevurdering.Innvilget> {
            on { attestering } doReturn Attestering.Iverksatt(NavIdentBruker.Attestant("attestantSomIverksatte"), fixedTidspunkt)
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
                        periode = periodeNesteMånedOgTreMånederFram,
                        begrunnelse = null,
                    ),
                ),
            )
            on { vilkårsvurderinger } doReturn Vilkårsvurderinger(
                uføre = vilkårsvurderingUføre,
            )
        }

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(
                søknadsbehandlingsvedtakIverksattInnvilget.copy(
                    beregning = lagBeregning(periodeNesteMånedOgTreMånederFram),
                ),
                Vedtak.from(revurdering, UUID30.randomUUID(), fixedClock),
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
                fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Bosituasjon),
            ),
        )

        actual shouldBe KunneIkkeOppdatereRevurdering.EpsInntektMedFlereBosituasjonsperioderMåRevurderes.left()
        verify(revurderingRepoMock).hent(revurderingId)
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(sakId, periodeNesteMånedOgTreMånederFram.fraOgMed)
        mocks.verifyNoMoreInteractions()
    }
}
