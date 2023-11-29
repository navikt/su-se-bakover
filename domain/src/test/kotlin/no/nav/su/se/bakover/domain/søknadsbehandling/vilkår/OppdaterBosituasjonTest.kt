package no.nav.su.se.bakover.domain.søknadsbehandling.vilkår

import arrow.core.nonEmptyListOf
import beregning.domain.fradrag.FradragTilhører
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Fradragsgrunnlag.Companion.harEpsInntekt
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt1000
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
import org.junit.jupiter.api.Test

internal class OppdaterBosituasjonTest {
    @Test
    fun `fjerner formue og fradrag for eps dersom bosituasjon oppdateres til enslig`() {
        val bosituasjonMedEps = bosituasjongrunnlagEpsUførFlyktning()
        søknadsbehandlingVilkårsvurdertInnvilget(
            customGrunnlag = listOf(
                bosituasjonMedEps,
                fradragsgrunnlagArbeidsinntekt1000(tilhører = FradragTilhører.EPS),
            ),
            customVilkår = listOf(
                formuevilkårMedEps0Innvilget(bosituasjon = nonEmptyListOf(bosituasjonMedEps)),
            ),
        ).second.let { original ->
            original.grunnlagsdata.fradragsgrunnlag.harEpsInntekt() shouldBe true
            original.vilkårsvurderinger.formue.harEPSFormue() shouldBe true

            original.oppdaterBosituasjon(
                bosituasjon = bosituasjongrunnlagEnslig(),
                saksbehandler = saksbehandler,
            ).getOrFail().also {
                it.grunnlagsdata.fradragsgrunnlag.harEpsInntekt() shouldBe false
                it.vilkårsvurderinger.formue.harEPSFormue() shouldBe false
            }
        }
    }
}
