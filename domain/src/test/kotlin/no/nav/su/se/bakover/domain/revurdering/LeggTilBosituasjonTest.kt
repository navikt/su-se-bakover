package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.grunnlag.harEPS
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test

class LeggTilBosituasjonTest {
    @Test
    fun `kan endre bosituasjon til enslig hvis man har eps med inntekter fra før`() {
        opprettetRevurdering(
            grunnlagsdataOverrides = listOf(
                bosituasjongrunnlagEpsUførFlyktning(
                    periode = periode2021,
                ),
                fradragsgrunnlagArbeidsinntekt(
                    periode = periode2021,
                    tilhører = FradragTilhører.EPS,
                    arbeidsinntekt = 10.000,
                ),
            ),
        ).let { (_, revurdering) ->
            revurdering.grunnlagsdata.bosituasjon.harEPS() shouldBe true
            revurdering.grunnlagsdata.fradragsgrunnlag.filter { it.tilhørerEps() } shouldHaveSize 1

            revurdering.oppdaterBosituasjonOgMarkerSomVurdert(
                bosituasjongrunnlagEnslig(periode = periode2021),
            ).getOrFail().let { oppdatert ->
                oppdatert.grunnlagsdata.bosituasjon.harEPS() shouldBe false
                oppdatert.grunnlagsdata.fradragsgrunnlag.filter { it.tilhørerEps() } shouldHaveSize 0
            }
        }
    }
}
