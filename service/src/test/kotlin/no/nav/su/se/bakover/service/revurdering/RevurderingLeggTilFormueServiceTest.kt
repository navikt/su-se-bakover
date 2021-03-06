package no.nav.su.se.bakover.service.revurdering

import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.createRevurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.søknadsbehandlingsvedtakIverksattInnvilget
import no.nav.su.se.bakover.service.søknadsbehandling.testBeregning
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.UUID

internal class RevurderingLeggTilFormueServiceTest {

    @Test
    fun `legg til revurdering av formue happy case`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        ).leggTilFormuegrunnlag(
            request = LeggTilFormuegrunnlagRequest(
                revurderingId = revurderingId,
                formuegrunnlag = Nel.fromListUnsafe(
                    listOf(
                        LeggTilFormuegrunnlagRequest.Grunnlag(
                            periode = periodeHele2021,
                            epsFormue = Formuegrunnlag.Verdier.empty(),
                            søkersFormue = Formuegrunnlag.Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        ).getOrHandle { fail("Vi skal få tilbake en revurdering") }

        actual shouldBe beOfType<OpprettetRevurdering>()

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(revurderingRepoMock).lagre(
                argThat {
                    it shouldBe opprettetRevurdering.copy(
                        behandlingsinformasjon = it.behandlingsinformasjon.copy(
                            formue = it.behandlingsinformasjon.formue!!.copy(
                                epsVerdier = Behandlingsinformasjon.Formue.Verdier(
                                    verdiIkkePrimærbolig = 0,
                                    verdiEiendommer = 0,
                                    verdiKjøretøy = 0,
                                    innskudd = 0,
                                    verdipapir = 0,
                                    pengerSkyldt = 0,
                                    kontanter = 0,
                                    depositumskonto = 0,
                                ),
                                verdier = Behandlingsinformasjon.Formue.Verdier(
                                    verdiIkkePrimærbolig = 0,
                                    verdiEiendommer = 0,
                                    verdiKjøretøy = 0,
                                    innskudd = 0,
                                    verdipapir = 0,
                                    pengerSkyldt = 0,
                                    kontanter = 0,
                                    depositumskonto = 0,
                                ),
                            ),
                        ),
                        informasjonSomRevurderes = it.informasjonSomRevurderes.markerSomVurdert(
                            Revurderingsteg.Formue,
                        ),
                    )
                },
            )
            verify(vilkårsvurderingServiceMock).lagre(
                argThat { it shouldBe revurderingId },
                argThat {
                    it shouldBe Vilkårsvurderinger(
                        uføre = Vilkår.Uførhet.Vurdert.create(
                            vurderingsperioder = nonEmptyListOf(
                                Vurderingsperiode.Uføre.create(
                                    id = (it.uføre as Vilkår.Uførhet.Vurdert).vurderingsperioder.first().id,
                                    opprettet = fixedTidspunkt,
                                    resultat = Resultat.Innvilget,
                                    grunnlag = Grunnlag.Uføregrunnlag(
                                        id = uføreId,
                                        opprettet = fixedTidspunkt,
                                        periode = periodeHele2021,
                                        uføregrad = Uføregrad.parse(20),
                                        forventetInntekt = 10,
                                    ),
                                    periode = periodeHele2021,
                                    begrunnelse = "ok2k",
                                ),
                            ),
                        ),
                        formue = Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
                            vurderingsperioder = nonEmptyListOf(
                                Vurderingsperiode.Formue.tryCreate(
                                    id = (it.formue as Vilkår.Formue.Vurdert).vurderingsperioder.first().id,
                                    opprettet = fixedTidspunkt,
                                    resultat = Resultat.Innvilget,
                                    grunnlag = Formuegrunnlag.create(
                                        id = (it.formue as Vilkår.Formue.Vurdert).grunnlag.first().id,
                                        periode = periodeHele2021,
                                        opprettet = fixedTidspunkt,
                                        epsFormue = Formuegrunnlag.Verdier.empty(),
                                        søkersFormue = Formuegrunnlag.Verdier.empty(),
                                        begrunnelse = null,
                                        bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                                            id = UUID.randomUUID(),
                                            fnr = fnr,
                                            opprettet = fixedTidspunkt,
                                            periode = periodeHele2021,
                                            begrunnelse = null,
                                        ),
                                        behandlingsPeriode = periodeHele2021,
                                    ),
                                    vurderingsperiode = periodeHele2021,
                                ).getOrHandle { fail("Skal returnere en formue") },
                            ),
                        ),
                    )
                },
            )
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock, vilkårsvurderingServiceMock)
    }

    @Test
    fun `skal ikke være lov å legge inn formue for eps, hvis man ikke har noen eps`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering.copy(
                grunnlagsdata = opprettetRevurdering.grunnlagsdata.copy(
                    bosituasjon = listOf(
                        Grunnlag.Bosituasjon.Fullstendig.Enslig(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = periodeHele2021,
                            begrunnelse = null,
                        ),
                    ),
                ),
            )
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        ).leggTilFormuegrunnlag(
            request = LeggTilFormuegrunnlagRequest(
                revurderingId = revurderingId,
                formuegrunnlag = Nel.fromListUnsafe(
                    listOf(
                        LeggTilFormuegrunnlagRequest.Grunnlag(
                            periode = periodeHele2021,
                            epsFormue = Formuegrunnlag.Verdier.empty(),
                            søkersFormue = Formuegrunnlag.Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        ).getOrHandle {
            it shouldBe KunneIkkeLeggeTilFormuegrunnlag.MåHaEpsHvisManHarSattEpsFormue
        }

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock, vilkårsvurderingServiceMock)
    }

    @Test
    fun `ikke lov å legge inn formue periode utenfor perioden til revurderingen`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        ).leggTilFormuegrunnlag(
            request = LeggTilFormuegrunnlagRequest(
                revurderingId = revurderingId,
                formuegrunnlag = Nel.fromListUnsafe(
                    listOf(
                        LeggTilFormuegrunnlagRequest.Grunnlag(
                            periode = periodeHele2020,
                            epsFormue = null,
                            søkersFormue = Formuegrunnlag.Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        ).getOrHandle {
            it shouldBe KunneIkkeLeggeTilFormuegrunnlag.FormuePeriodeErUtenforBehandlingsperioden
        }

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock, vilkårsvurderingServiceMock)
    }

    @Test
    fun `ikke lov å legge inn epsformue periode utenfor perioden til revurderingen`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        ).leggTilFormuegrunnlag(
            request = LeggTilFormuegrunnlagRequest(
                revurderingId = revurderingId,
                formuegrunnlag = Nel.fromListUnsafe(
                    listOf(
                        LeggTilFormuegrunnlagRequest.Grunnlag(
                            periode = periodeHele2020,
                            epsFormue = Formuegrunnlag.Verdier.empty(),
                            søkersFormue = Formuegrunnlag.Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        ).getOrHandle {
            it shouldBe KunneIkkeLeggeTilFormuegrunnlag.EpsFormueperiodeErUtenforBosituasjonPeriode
        }

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock, vilkårsvurderingServiceMock)
    }

    @Test
    fun `ikke lov å legge inn formue med overlappende perioder`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        ).leggTilFormuegrunnlag(
            request = LeggTilFormuegrunnlagRequest(
                revurderingId = revurderingId,
                formuegrunnlag = Nel.fromListUnsafe(
                    listOf(
                        LeggTilFormuegrunnlagRequest.Grunnlag(
                            periode = periodeJanMars,
                            epsFormue = Formuegrunnlag.Verdier.empty(),
                            søkersFormue = Formuegrunnlag.Verdier.empty(),
                            begrunnelse = null,
                        ),
                        LeggTilFormuegrunnlagRequest.Grunnlag(
                            periode = periodeMarsDesember,
                            epsFormue = null,
                            søkersFormue = Formuegrunnlag.Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        ).getOrHandle {
            it shouldBe KunneIkkeLeggeTilFormuegrunnlag.IkkeLovMedOverlappendePerioder
        }

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock, vilkårsvurderingServiceMock)
    }

    @Test
    fun `feilmelding hvis vi ikke finner revurdering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn null
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        ).leggTilFormuegrunnlag(
            request = LeggTilFormuegrunnlagRequest(
                revurderingId = revurderingId,
                formuegrunnlag = Nel.fromListUnsafe(
                    listOf(
                        LeggTilFormuegrunnlagRequest.Grunnlag(
                            periode = periodeHele2021,
                            epsFormue = Formuegrunnlag.Verdier.empty(),
                            søkersFormue = Formuegrunnlag.Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        ).getOrHandle {
            it shouldBe KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering
        }

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock, vilkårsvurderingServiceMock)
    }

    @Test
    fun `kan ikke legge inn formue når revurdering er til attestering`() {
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurderingTilAttestering
        }

        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        ).leggTilFormuegrunnlag(
            request = LeggTilFormuegrunnlagRequest(
                revurderingId = revurderingId,
                formuegrunnlag = Nel.fromListUnsafe(
                    listOf(
                        LeggTilFormuegrunnlagRequest.Grunnlag(
                            periode = periodeHele2021,
                            epsFormue = Formuegrunnlag.Verdier.empty(),
                            søkersFormue = Formuegrunnlag.Verdier.empty(),
                            begrunnelse = null,
                        ),
                    ),
                ),
            ),
        ).getOrHandle {
            it shouldBe KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand(
                fra = RevurderingTilAttestering.Innvilget::class,
                til = OpprettetRevurdering::class,
            )
        }

        inOrder(
            revurderingRepoMock,
            vilkårsvurderingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
        }
        verifyNoMoreInteractions(revurderingRepoMock, vilkårsvurderingServiceMock)
    }

    private val fnr = FnrGenerator.random()
    private val periodeHele2021 = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021))
    private val periodeJanMars = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mars(2021))
    private val periodeMarsDesember = Periode.create(fraOgMed = 1.mars(2021), tilOgMed = 31.desember(2021))
    private val periodeHele2020 = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.mars(2020))
    private val uføreId = UUID.randomUUID()

    private val vilkårsvurderinger = Vilkårsvurderinger(
        uføre = Vilkår.Uførhet.Vurdert.create(
            vurderingsperioder = nonEmptyListOf(
                Vurderingsperiode.Uføre.create(
                    id = uføreId,
                    opprettet = fixedTidspunkt,
                    resultat = Resultat.Innvilget,
                    grunnlag = Grunnlag.Uføregrunnlag(
                        id = uføreId,
                        opprettet = fixedTidspunkt,
                        periode = periodeHele2021,
                        uføregrad = Uføregrad.parse(20),
                        forventetInntekt = 10,
                    ),
                    periode = periodeHele2021,
                    begrunnelse = "ok2k",
                ),
            ),
        ),
    )

    private val opprettetRevurdering = OpprettetRevurdering(
        id = revurderingId,
        periode = periodeHele2021,
        opprettet = fixedTidspunkt,
        tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
        saksbehandler = saksbehandler,
        oppgaveId = OppgaveId("oppgaveid"),
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = null,
        behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
        grunnlagsdata = Grunnlagsdata(
            fradragsgrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 10000.0,
                        periode = søknadsbehandlingsvedtakIverksattInnvilget.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                    id = UUID.randomUUID(),
                    fnr = fnr,
                    opprettet = fixedTidspunkt,
                    periode = periodeHele2021,
                    begrunnelse = null,
                ),
            ),
        ),
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
    )

    private val revurderingTilAttestering = RevurderingTilAttestering.Innvilget(
        id = revurderingId,
        periode = periodeHele2021,
        opprettet = fixedTidspunkt,
        tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
        saksbehandler = saksbehandler,
        oppgaveId = OppgaveId("oppgaveid"),
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
        behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
        beregning = testBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "gjelderNavn",
            datoBeregnet = fixedTidspunkt.toLocalDate(zoneIdOslo),
            nettoBeløp = 100,
            periodeList = emptyList(),
        ),
        grunnlagsdata = Grunnlagsdata(
            fradragsgrunnlag = listOf(
                Grunnlag.Fradragsgrunnlag(
                    fradrag = FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 10000.0,
                        periode = søknadsbehandlingsvedtakIverksattInnvilget.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    opprettet = fixedTidspunkt,
                ),
            ),
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                    id = UUID.randomUUID(),
                    fnr = fnr,
                    opprettet = fixedTidspunkt,
                    periode = periodeHele2021,
                    begrunnelse = null,
                ),
            ),
        ),
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        attesteringer = Attesteringshistorikk.empty()
    )
}
