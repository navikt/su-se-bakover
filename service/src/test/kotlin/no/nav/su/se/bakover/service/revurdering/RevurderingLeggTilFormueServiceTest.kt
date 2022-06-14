package no.nav.su.se.bakover.service.revurdering

import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import io.kotest.assertions.arrow.core.shouldHaveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.lagFradragsgrunnlag
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class RevurderingLeggTilFormueServiceTest {

    @Test
    fun `legg til revurdering av formue happy case`() {
        val nyFormue = LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
            periode = år(2021),
            epsFormue = Formuegrunnlag.Verdier.empty(),
            søkersFormue = Formuegrunnlag.Verdier.empty(),
            begrunnelse = null,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering(
                    informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Formue)),
                    grunnlagsdataOverrides = listOf(
                        lagFradragsgrunnlag(
                            type = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 10000.0,
                            periode = år(2021),
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                            id = UUID.randomUUID(),
                            fnr = epsFnr,
                            opprettet = fixedTidspunkt,
                            periode = år(2021),
                        ),
                    ),
                ).second
            },
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = Nel.fromListUnsafe(listOf(nyFormue)),
                ),
            ).getOrFail()

            actual.shouldBeType<RevurderingOgFeilmeldingerResponse>().let { response ->
                response.revurdering.informasjonSomRevurderes shouldBe InformasjonSomRevurderes.create(
                    mapOf(Revurderingsteg.Formue to Vurderingstatus.Vurdert),
                )
                response.revurdering.vilkårsvurderinger.formue.shouldBeType<Vilkår.Formue.Vurdert>().let {
                    it.vurderingsperioder shouldHaveSize 1
                    it.vurderingsperioder.single().periode shouldBe nyFormue.periode
                    it.resultat shouldBe Resultat.Innvilget
                    it.grunnlag shouldHaveSize 1
                    it.grunnlag.single().periode shouldBe nyFormue.periode
                    it.grunnlag.single().epsFormue shouldBe nyFormue.epsFormue
                    it.grunnlag.single().søkersFormue shouldBe nyFormue.søkersFormue
                }
            }
            verify(serviceAndMocks.revurderingRepo).hent(revurderingId)
            verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
            verify(serviceAndMocks.revurderingRepo).lagre(
                argThat { it shouldBe actual.revurdering },
                anyOrNull(),
            )
        }
    }

    @Test
    fun `skal ikke være lov å legge inn formue for eps, hvis man ikke har noen eps`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering(
                    grunnlagsdataOverrides = listOf(
                        Grunnlag.Bosituasjon.Fullstendig.Enslig(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            periode = år(2021),
                        ),
                    ),
                ).second
            },
        ).let {
            it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = Nel.fromListUnsafe(
                        listOf(
                            LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                                periode = år(2021),
                                epsFormue = Formuegrunnlag.Verdier.empty(),
                                søkersFormue = Formuegrunnlag.Verdier.empty(),
                                begrunnelse = null,
                            ),
                        ),
                    ),
                ),
            ).getOrHandle {
                it shouldBe KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet(
                    LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.Konsistenssjekk(
                        Konsistensproblem.BosituasjonOgFormue.KombinasjonAvBosituasjonOgFormueErUyldig,
                    ),
                )
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingId)
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `ikke lov å legge inn formue periode utenfor perioden til revurderingen`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering(
                    revurderingsperiode = år(2021),
                ).second
            },
        ).let {
            it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = Nel.fromListUnsafe(
                        listOf(
                            LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                                periode = år(2020),
                                epsFormue = null,
                                søkersFormue = Formuegrunnlag.Verdier.empty(),
                                begrunnelse = null,
                            ),
                        ),
                    ),
                ),
            ).getOrHandle {
                it shouldBe KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet(LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.FormuePeriodeErUtenforBehandlingsperioden)
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingId)
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `ikke lov å legge inn epsformue periode utenfor perioden til revurderingen`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering(
                    revurderingsperiode = år(2021),
                ).second
            },
        ).let {
            it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = Nel.fromListUnsafe(
                        listOf(
                            LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                                periode = Periode.create(1.januar(2021), 31.mars(2021)),
                                epsFormue = null,
                                søkersFormue = Formuegrunnlag.Verdier.empty(),
                                begrunnelse = null,
                            ),
                        ),
                    ),
                ),
            ).getOrHandle {
                it shouldBe KunneIkkeLeggeTilFormuegrunnlag.Konsistenssjekk(
                    Konsistensproblem.BosituasjonOgFormue.IngenFormueForBosituasjonsperiode,
                )
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingId)
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `ikke lov å legge inn formue med overlappende perioder`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering(
                    grunnlagsdataOverrides = listOf(
                        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                            id = UUID.randomUUID(),
                            fnr = epsFnr,
                            opprettet = fixedTidspunkt,
                            periode = år(2021),
                        ),
                    ),
                ).second
            },
        ).let {
            it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = Nel.fromListUnsafe(
                        listOf(
                            LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                                periode = periodeJanMars2021,
                                epsFormue = Formuegrunnlag.Verdier.empty(),
                                søkersFormue = Formuegrunnlag.Verdier.empty(),
                                begrunnelse = null,
                            ),
                            LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                                periode = periodeMarsDesember2021,
                                epsFormue = Formuegrunnlag.Verdier.empty(),
                                søkersFormue = Formuegrunnlag.Verdier.empty(),
                                begrunnelse = null,
                            ),
                        ),
                    ),
                ),
            ).getOrHandle {
                it shouldBe KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet(LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.IkkeLovMedOverlappendePerioder)
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingId)
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `feilmelding hvis vi ikke finner revurdering`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.revurderingService.leggTilFormuegrunnlag(
                LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = Nel.fromListUnsafe(
                        listOf(
                            LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                                periode = år(2021),
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
        }
    }

    @Test
    fun `når formue blir avslått, og uførhet er det også, får vi feil om at utfallet ikke støttes pga opphør av flere vilkår`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn opprettetRevurdering(
                    grunnlagsdataOverrides = listOf(
                        Grunnlag.Bosituasjon.Fullstendig.Enslig(
                            id = UUID.randomUUID(), opprettet = fixedTidspunkt,
                            periode = stønadsperiode2021.periode,
                        ),
                    ),
                    vilkårOverrides = listOf(
                        avslåttUførevilkårUtenGrunnlag(
                            periode = år(2021),
                        ),
                        formuevilkårIkkeVurdert(),
                        UtenlandsoppholdVilkår.IkkeVurdert,
                    ),
                ).second
            },
        ).let {
            val response = it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = nonEmptyListOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = år(2021),
                            epsFormue = null,
                            søkersFormue = Formuegrunnlag.Verdier.create(
                                verdiIkkePrimærbolig = 10000000,
                                verdiEiendommer = 0,
                                verdiKjøretøy = 0,
                                innskudd = 0,
                                verdipapir = 0,
                                pengerSkyldt = 0,
                                kontanter = 0,
                                depositumskonto = 0,
                            ),
                            begrunnelse = ":(",
                        ),
                    ),
                ),
            ).getOrFail()

            response.feilmeldinger.shouldContain(RevurderingsutfallSomIkkeStøttes.OpphørAvFlereVilkår)

            verify(it.revurderingRepo).hent(revurderingId)
            verify(it.revurderingRepo).defaultTransactionContext()
            verify(it.revurderingRepo).lagre(
                argThat { it shouldBe response.revurdering },
                anyOrNull(),
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får feilmelding om at opphør ikke er fra første måned i revurderingsperioden`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn opprettetRevurdering().second
            },
        ).let {
            val response = it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = nonEmptyListOf(
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = Periode.create(1.januar(2021), 31.mai(2021)),
                            epsFormue = null,
                            søkersFormue = Formuegrunnlag.Verdier.empty(),
                            begrunnelse = ":)",
                        ),
                        LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                            periode = Periode.create(1.juni(2021), 31.desember(2021)),
                            epsFormue = null,
                            søkersFormue = Formuegrunnlag.Verdier.create(
                                verdiIkkePrimærbolig = 10000000,
                                verdiEiendommer = 0,
                                verdiKjøretøy = 0,
                                innskudd = 0,
                                verdipapir = 0,
                                pengerSkyldt = 0,
                                kontanter = 0,
                                depositumskonto = 0,
                            ),
                            begrunnelse = ":(",
                        ),
                    ),
                ),
            ).getOrFail()

            response.feilmeldinger.shouldContain(RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned)
        }
    }

    @Test
    fun `kan ikke legge inn formue når revurdering er til attestering`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering().second
            },
        ).let {
            val response = it.revurderingService.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = revurderingId,
                    formuegrunnlag = Nel.fromListUnsafe(
                        listOf(
                            LeggTilFormuevilkårRequest.Grunnlag.Revurdering(
                                periode = år(2021),
                                epsFormue = null,
                                søkersFormue = Formuegrunnlag.Verdier.empty(),
                                begrunnelse = null,
                            ),
                        ),
                    ),
                ),
            )

            response shouldBe KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand(
                fra = RevurderingTilAttestering.Innvilget::class,
                til = OpprettetRevurdering::class,
            ).left()
        }
    }

    private val periodeJanMars2021 = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mars(2021))
    private val periodeMarsDesember2021 = Periode.create(fraOgMed = 1.mars(2021), tilOgMed = 31.desember(2021))
}
