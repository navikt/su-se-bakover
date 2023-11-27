package no.nav.su.se.bakover.domain.revurdering

import behandling.domain.beregning.fradrag.FradragTilhører
import behandling.domain.beregning.fradrag.Fradragstype
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioderMedEPS
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioderUtenEPS
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.fullstendigMedEPS
import no.nav.su.se.bakover.test.fullstendigUtenEPS
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.vilkår.innvilgetFormueVilkår
import org.junit.jupiter.api.Test
import java.util.UUID

class LeggTilBosituasjonTest {
    @Test
    fun `fjerner eventuelle fradrag for EPS i perioder hvor bosituasjon endres til å være enslig`() {
        val bosituasjon = bosituasjongrunnlagEpsUførFlyktning(
            periode = år(2021),
        )
        opprettetRevurdering(
            vilkårOverrides = listOf(
                innvilgetFormueVilkår(
                    periode = år(2021),
                    bosituasjon = bosituasjon,
                ),
            ),
            grunnlagsdataOverrides = listOf(
                bosituasjon,
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    tilhører = FradragTilhører.EPS,
                    arbeidsinntekt = 10_000.0,
                ),
            ),
        ).let { (_, revurdering) ->
            revurdering.grunnlagsdata.bosituasjon.harEPS() shouldBe true
            revurdering.grunnlagsdata.fradragsgrunnlag.filter { it.tilhørerEps() } shouldHaveSize 1
            revurdering.vilkårsvurderinger.formue.harEPSFormue() shouldBe true

            revurdering.oppdaterBosituasjonOgMarkerSomVurdert(
                listOf(bosituasjongrunnlagEnslig(periode = år(2021))),
            ).getOrFail().let { oppdatert ->
                oppdatert.grunnlagsdata.bosituasjon.harEPS() shouldBe false
                oppdatert.grunnlagsdata.fradragsgrunnlag.filter { it.tilhørerEps() } shouldHaveSize 0
                oppdatert.vilkårsvurderinger.formue.harEPSFormue() shouldBe false
            }

            revurdering.oppdaterBosituasjonOgMarkerSomVurdert(
                listOf(
                    bosituasjongrunnlagEpsUførFlyktning(periode = Periode.create(1.januar(2021), 30.september(2021))),
                    bosituasjongrunnlagEnslig(periode = Periode.create(1.oktober(2021), 31.desember(2021))),
                ),
            ).getOrFail().let { oppdatert ->
                oppdatert.grunnlagsdata.bosituasjon.harEPS() shouldBe true
                oppdatert.grunnlagsdata.bosituasjon.perioderMedEPS().single() shouldBe Periode.create(
                    1.januar(2021),
                    30.september(2021),
                )
                oppdatert.grunnlagsdata.bosituasjonSomFullstendig().perioderUtenEPS().single() shouldBe Periode.create(
                    1.oktober(2021),
                    31.desember(2021),
                )
                oppdatert.grunnlagsdata.fradragsgrunnlag.single { it.tilhørerEps() }.let {
                    it.periode shouldBe Periode.create(
                        1.januar(2021),
                        30.september(2021),
                    )
                    it.fradragstype shouldBe Fradragstype.Arbeidsinntekt
                    it.månedsbeløp shouldBe 10_000.0
                }
                oppdatert.vilkårsvurderinger.formue.harEPSFormue() shouldBe true
            }
        }
    }

    @Test
    fun `bevarer eventuelle fradrag og formue for EPS dersom bosituasjon endres til EPS`() {
        val bosituasjon = bosituasjongrunnlagEpsUførFlyktning(
            periode = år(2021),
        )
        opprettetRevurdering(
            vilkårOverrides = listOf(
                innvilgetFormueVilkår(
                    periode = år(2021),
                    bosituasjon = bosituasjon,
                ),
            ),
            grunnlagsdataOverrides = listOf(
                bosituasjon,
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    tilhører = FradragTilhører.EPS,
                    arbeidsinntekt = 10_000.0,
                ),
            ),
        ).let { (_, revurdering) ->
            revurdering.grunnlagsdata.bosituasjon.harEPS() shouldBe true
            revurdering.grunnlagsdata.fradragsgrunnlag.filter { it.tilhørerEps() } shouldHaveSize 1
            revurdering.vilkårsvurderinger.formue.harEPSFormue() shouldBe true

            revurdering.oppdaterBosituasjonOgMarkerSomVurdert(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = år(2021),
                        fnr = epsFnr,
                    ),
                ),
            ).getOrFail().let { oppdatert ->
                oppdatert.grunnlagsdata.bosituasjon.harEPS() shouldBe true
                oppdatert.grunnlagsdata.fradragsgrunnlag.filter { it.tilhørerEps() } shouldHaveSize 1
                oppdatert.vilkårsvurderinger.formue.harEPSFormue() shouldBe true
            }
        }
    }

    @Test
    fun `legger til tom formue for EPS dersom bosituasjon endres til å ha EPS`() {
        val bosituasjon = fullstendigUtenEPS(år(2021))
        opprettetRevurdering(
            vilkårOverrides = listOf(
                innvilgetFormueVilkår(
                    periode = år(2021),
                    bosituasjon = bosituasjon,
                ),
            ),
            grunnlagsdataOverrides = listOf(
                bosituasjon,
            ),
        ).let { (_, revurdering) ->
            revurdering.grunnlagsdata.bosituasjon.harEPS() shouldBe false
            revurdering.vilkårsvurderinger.formue.harEPSFormue() shouldBe false
            revurdering.oppdaterBosituasjonOgMarkerSomVurdert(
                bosituasjon = listOf(
                    fullstendigMedEPS(år(2021)),
                ),
            ).getOrFail().let { oppdatert ->
                oppdatert.grunnlagsdata.bosituasjon.harEPS() shouldBe true
                oppdatert.vilkårsvurderinger.formue.harEPSFormue() shouldBe true
                oppdatert.vilkårsvurderinger.formue.grunnlag.single().epsFormue shouldBe Formuegrunnlag.Verdier.create(
                    verdiIkkePrimærbolig = 0,
                    verdiEiendommer = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0,
                )
            }
        }
    }
}
