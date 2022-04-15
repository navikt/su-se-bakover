package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harEPS
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioderMedEPS
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioderUtenEPS
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.epsFnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.innvilgetFormueVilkår
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import java.util.UUID

class LeggTilBosituasjonTest {
    @Test
    fun `fjerner eventuelle fradrag og formue for EPS dersom bosituasjon endres til enslig`() {
        val bosituasjon = bosituasjongrunnlagEpsUførFlyktning(
            periode = periode2021,
        )
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                bosituasjon,
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    tilhører = FradragTilhører.EPS,
                    arbeidsinntekt = 10.000,
                ),
            ),
            vilkårOverrides = listOf(
                innvilgetFormueVilkår(
                    periode = periode2021,
                    bosituasjon = bosituasjon,
                ),
            ),
        ).let { (_, revurdering) ->
            revurdering.grunnlagsdata.bosituasjon.harEPS() shouldBe true
            revurdering.grunnlagsdata.fradragsgrunnlag.filter { it.tilhørerEps() } shouldHaveSize 1
            revurdering.vilkårsvurderinger.formue.harEPSFormue() shouldBe true

            revurdering.oppdaterBosituasjonOgMarkerSomVurdert(
                listOf(bosituasjongrunnlagEnslig(periode = periode2021)),
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
                oppdatert.grunnlagsdata.bosituasjon.perioderUtenEPS().single() shouldBe Periode.create(
                    1.oktober(2021),
                    31.desember(2021),
                )
                oppdatert.grunnlagsdata.fradragsgrunnlag.filter { it.tilhørerEps() } shouldHaveSize 1
                oppdatert.vilkårsvurderinger.formue.harEPSFormue() shouldBe true
            }
        }
    }

    @Test
    fun `bevarer eventuelle fradrag og formue for EPS dersom bosituasjon endres til EPS`() {
        val bosituasjon = bosituasjongrunnlagEpsUførFlyktning(
            periode = periode2021,
        )
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                bosituasjon,
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    tilhører = FradragTilhører.EPS,
                    arbeidsinntekt = 10.000,
                ),
            ),
            vilkårOverrides = listOf(
                innvilgetFormueVilkår(
                    periode = periode2021,
                    bosituasjon = bosituasjon,
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
                        periode = periode2021,
                        fnr = epsFnr,
                        begrunnelse = null,
                    ),
                ),
            ).getOrFail().let { oppdatert ->
                oppdatert.grunnlagsdata.bosituasjon.harEPS() shouldBe true
                oppdatert.grunnlagsdata.fradragsgrunnlag.filter { it.tilhørerEps() } shouldHaveSize 1
                oppdatert.vilkårsvurderinger.formue.harEPSFormue() shouldBe true
            }
        }
    }
}
