package no.nav.su.se.bakover.service.revurdering

import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.formueVilkår
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.innvilgetUførevilkårForventetInntekt12000
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.utlandsoppholdAvslag
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class OppdaterRevurderingServiceTest {

    private val sakOgIverksattInnvilgetSøknadsbehandlingsvedtak = vedtakSøknadsbehandlingIverksattInnvilget(
        stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
    )

    // Merk at søknadsbehandlingens uførevilkår ikke er likt som OpprettetRevurdering før vi kaller oppdater-funksjonen, men vi forventer at de er like etter oppdateringa.
    private val vilkårsvurderingUføre =
        innvilgetUførevilkårForventetInntekt12000(periode = periodeNesteMånedOgTreMånederFram)

    // TODO jah: Vi burde ha en domeneklasse/factory som oppretter en revurdering fra et Vedtak, så slipper vi å gjøre disse antagelsene i testene
    private val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
        stønadsperiode = stønadsperiodeNesteMånedOgTreMånederFram,
        revurderingsperiode = periodeNesteMånedOgTreMånederFram,
        sakOgVedtakSomKanRevurderes = sakOgIverksattInnvilgetSøknadsbehandlingsvedtak,
    ).second.copy(
        grunnlagsdata = Grunnlagsdata.create(
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = periodeNesteMånedOgTreMånederFram,
                    begrunnelse = null,
                ),
            ),
        ),
        vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
            uføre = vilkårsvurderingUføre,
            formue = Vilkår.Formue.IkkeVurdert,
            utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
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
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn KunneIkkeKopiereGjeldendeVedtaksdata.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
                .left()
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

        actual shouldBe KunneIkkeOppdatereRevurdering.UgyldigPeriode(Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden)
            .left()
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
                forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
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
                forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("begrunnelse"),
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
            vedtakListe = nonEmptyListOf(sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second),
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
                            opprettet = fixedTidspunkt,
                        ),
                    ),
                    simulering = mock(),
                    forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
                    grunnlagsdata = Grunnlagsdata.create(
                        bosituasjon = listOf(
                            Grunnlag.Bosituasjon.Fullstendig.Enslig(
                                id = UUID.randomUUID(),
                                opprettet = fixedTidspunkt,
                                periode = it.periode,
                                begrunnelse = null,
                            ),
                        ),
                    ),
                    vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
                    informasjonSomRevurderes = it.informasjonSomRevurderes,
                    avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
                )
            }
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            avkortingsvarselRepo = mock() {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
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
        verify(mocks.avkortingsvarselRepo).hentUtestående(any())
        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
            opprettetRevurdering.sakId,
            opprettetRevurdering.periode.fraOgMed,
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppdater en revurdering`() {
        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second),
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
            avkortingsvarselRepo = mock() {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
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
            oppdatertRevurdering.tilRevurdering shouldBe sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second
            oppdatertRevurdering.saksbehandler shouldBe saksbehandler
            oppdatertRevurdering.oppgaveId shouldBe oppgaveIdRevurdering
            oppdatertRevurdering.fritekstTilBrev shouldBe ""
            oppdatertRevurdering.revurderingsårsak shouldBe Revurderingsårsak(
                årsak = Revurderingsårsak.Årsak.ANDRE_KILDER,
                begrunnelse = Revurderingsårsak.Begrunnelse.create("bør bli oppdatert"),
            )
            oppdatertRevurdering.forhåndsvarsel shouldBe null
            oppdatertRevurdering.vilkårsvurderinger.uføre.grunnlag.let {
                it shouldHaveSize 1
                it[0].ekvivalentMed(sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second.behandling.vilkårsvurderinger.uføre.grunnlag.first())
            }
            oppdatertRevurdering.vilkårsvurderinger.uføre.ekvivalentMed(sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second.behandling.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert)
            oppdatertRevurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert))
        }

        inOrder(
            *mocks.all(),
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
                sakId = argThat { it shouldBe sakId },
                fraOgMed = argThat {
                    it shouldBe oppdatertPeriode.fraOgMed
                },
            )
            verify(mocks.avkortingsvarselRepo).hentUtestående(any())
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual }, anyOrNull())
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
            vedtakListe = nonEmptyListOf(sakOgIverksattInnvilgetSøknadsbehandlingsvedtak.second),
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
                grunnlagsdata = Grunnlagsdata.create(),
                vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            )
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
            grunnlagService = grunnlagServiceMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
            avkortingsvarselRepo = mock() {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
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
            *mocks.all(),
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
                opprettetRevurdering.sakId,
                opprettetRevurdering.periode.fraOgMed,
            )
            verify(mocks.avkortingsvarselRepo).hentUtestående(any())
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(eq(actual), anyOrNull())
            verify(vilkårsvurderingServiceMock).lagre(actual.id, actual.vilkårsvurderinger)
            verify(grunnlagServiceMock).lagreFradragsgrunnlag(actual.id, actual.grunnlagsdata.fradragsgrunnlag)
            verify(grunnlagServiceMock).lagreBosituasjongrunnlag(actual.id, actual.grunnlagsdata.bosituasjon)
        }
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `støtter ikke tilfeller hvor gjeldende vedtaksdata ikke er sammenhengende i tid`() {
        val førsteVedtak = vedtakSøknadsbehandlingIverksattInnvilget().second
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
                    opprettet = fixedTidspunkt,
                ),
            ),
        )
        val andreVedtakFormueVilkår = formueVilkår(periodePlussEtÅr)
        val andreVedtak = vedtakSøknadsbehandlingIverksattInnvilget().second.copy(
            periode = periodePlussEtÅr,
            behandling = vedtakSøknadsbehandlingIverksattInnvilget().second.behandling.copy(
                stønadsperiode = Stønadsperiode.create(periodePlussEtÅr),
                grunnlagsdata = Grunnlagsdata.create(bosituasjon = listOf(bosituasjon)),
                vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                    uførevilkår,
                    andreVedtakFormueVilkår,
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
            on { attestering } doReturn Attestering.Iverksatt(
                NavIdentBruker.Attestant("attestantSomIverksatte"),
                fixedTidspunkt,
            )

            on { periode } doReturn revurderingsperiode
            on { beregning } doReturn revurderingBeregning
            on { simulering } doReturn mock()
            on { saksbehandler } doReturn mock()
            on { grunnlagsdata } doReturn Grunnlagsdata.create(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = periodeNesteMånedOgTreMånederFram,
                        begrunnelse = null,
                    ),
                    Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = periodeNesteMånedOgTreMånederFram,
                        begrunnelse = null,
                    ),
                ),
            )
            on { vilkårsvurderinger } doReturn Vilkårsvurderinger.Revurdering(
                uføre = vilkårsvurderingUføre,
                formue = Vilkår.Formue.IkkeVurdert,
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
            )
        }

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = periodeNesteMånedOgTreMånederFram,
            vedtakListe = nonEmptyListOf(
                vedtakSøknadsbehandlingIverksattInnvilget().second.copy(
                    beregning = lagBeregning(periodeNesteMånedOgTreMånederFram),
                ),
                VedtakSomKanRevurderes.from(revurdering, UUID30.randomUUID(), fixedClock),
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
        val vedtattSøknadsbehandling = vedtakSøknadsbehandlingIverksattInnvilget(
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode2021.periode,
                        begrunnelse = null,
                    ),
                ),
            ),
        )

        val periodeMedEPS = Periode.create(1.juni(2021), 31.desember(2021))
        val vedtattRevurdering = vedtakRevurderingIverksattInnvilget(
            revurderingsperiode = periodeMedEPS,
            sakOgVedtakSomKanRevurderes = vedtattSøknadsbehandling,
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger(
                grunnlagsdata = Grunnlagsdata.create(
                    bosituasjon = listOf(
                        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = periodeMedEPS,
                            fnr = Fnr.generer(),
                            begrunnelse = "giftet seg",
                        ),
                    ),
                    fradragsgrunnlag = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periodeMedEPS,
                            5000.0,
                            FradragTilhører.EPS,
                        ),
                        fradragsgrunnlagArbeidsinntekt(
                            periodeMedEPS,
                            5000.0,
                            FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                vilkårsvurderinger = vilkårsvurderingerInnvilget(periodeMedEPS),
            ),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn vedtattRevurdering.second.behandling as Revurdering
        }

        val gjeldendeVedtaksdata = GjeldendeVedtaksdata(
            periode = stønadsperiode2021.periode,
            vedtakListe = NonEmptyList.fromListUnsafe(vedtattRevurdering.first.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()),
            clock = fixedClock,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn gjeldendeVedtaksdata.right()
        }

        val mocks = RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            revurderingRepo = revurderingRepoMock,
        )
        val actual = mocks.revurderingService.opprettRevurdering(
            OpprettRevurderingRequest(
                sakId = vedtattRevurdering.first.id,
                fraOgMed = vedtattSøknadsbehandling.second.periode.fraOgMed,
                årsak = "MELDING_FRA_BRUKER",
                begrunnelse = "Ny informasjon",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Bosituasjon),
            ),
        )

        actual shouldBe KunneIkkeOppretteRevurdering.BosituasjonMedFlerePerioderMåRevurderes.left()
        verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
            vedtattRevurdering.first.id,
            vedtattSøknadsbehandling.second.periode.fraOgMed,
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `får lov til å oppdatere revurdering dersom periode overlapper opphørsvedtak for utenlandsopphold som ikke førte til avkorting`() {
        val tikkendeKlokke = TikkendeKlokke()

        val sakOgSøknadsvedtak = vedtakSøknadsbehandlingIverksattInnvilget(
            saksnummer = saksnummer,
            stønadsperiode = stønadsperiode2021,
            clock = tikkendeKlokke,
        )

        val revurderingsperiode = Periode.create(1.oktober(2021), 31.desember(2021))
        val sakOgSøknadsvedtakOgRevurderingsvedtak = vedtakRevurdering(
            clock = tikkendeKlokke,
            revurderingsperiode = revurderingsperiode,
            sakOgVedtakSomKanRevurderes = sakOgSøknadsvedtak,
            vilkårOverrides = listOf(
                utlandsoppholdAvslag(
                    periode = revurderingsperiode,
                ),
            ),
        )
        val (sak3, opprettetRevurdering) = opprettetRevurdering(
            sakOgVedtakSomKanRevurderes = sakOgSøknadsvedtakOgRevurderingsvedtak,
        )

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn GjeldendeVedtaksdata(
                    periode = revurderingsperiode,
                    vedtakListe = NonEmptyList.fromListUnsafe(sak3.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()),
                    clock = fixedClock,
                ).right()
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn Avkortingsvarsel.Ingen
            },
            vilkårsvurderingService = mock {
                doNothing().whenever(it).lagre(any(), any())
            },
            grunnlagService = mock {
                doNothing().whenever(it).lagreFradragsgrunnlag(any(), any())
            }
        )

        serviceAndMocks.revurderingService.oppdaterRevurdering(
            oppdaterRevurderingRequest = OppdaterRevurderingRequest(
                revurderingId = opprettetRevurdering.id,
                fraOgMed = 1.oktober(2021),
                årsak = Revurderingsårsak.Årsak.ANDRE_KILDER.toString(),
                begrunnelse = "lol",
                saksbehandler = saksbehandler,
                informasjonSomRevurderes = listOf(Revurderingsteg.Utenlandsopphold),
            ),
        ).getOrFail() shouldBe beOfType<OpprettetRevurdering>()
    }

    @Test
    fun `får feilmelding dersom saken har utestående avkorting, men revurderingsperioden inneholder ikke perioden for avkortingen`() {
        val clock = TikkendeKlokke(fixedClock)
        val (sak, opphørUtenlandsopphold) = vedtakRevurdering(
            clock = clock,
            revurderingsperiode = Periode.create(1.juni(2021), 31.desember(2021)),
            vilkårOverrides = listOf(
                utlandsoppholdAvslag(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = Periode.create(1.juni(2021), 31.desember(2021)),
                ),
            ),
        )
        val nyRevurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021))
        val uteståendeAvkorting =
            (opphørUtenlandsopphold as VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering).behandling.avkorting.let {
                (it as AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel).avkortingsvarsel
            }

        val opprettetRevurdering = opprettetRevurdering().second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering
            },
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = nyRevurderingsperiode.fraOgMed,
                    clock = clock,
                ).getOrFail().right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            avkortingsvarselRepo = mock {
                on { hentUtestående(any()) } doReturn uteståendeAvkorting
            },
        ).let {
            it.revurderingService.oppdaterRevurdering(
                OppdaterRevurderingRequest(
                    revurderingId = sakId,
                    fraOgMed = nyRevurderingsperiode.fraOgMed,
                    årsak = "MELDING_FRA_BRUKER",
                    begrunnelse = "Ny informasjon",
                    saksbehandler = saksbehandler,
                    informasjonSomRevurderes = listOf(Revurderingsteg.Utenlandsopphold),
                ),
            ) shouldBe KunneIkkeOppdatereRevurdering.UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(juni(2021))
                .left()
        }
    }
}
