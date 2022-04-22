package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.reduser
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.opprettetRevurdering
import org.junit.jupiter.api.Test

internal class BeregningMedFlereSatserTest {
    @Test
    fun `kan beregne med flere forskjellig strategier for samme beregning`() {
        val revurdering = opprettetRevurdering(
            revurderingsperiode = Periode.create(1.januar(2021), 31.desember(2021)),
            grunnlagsdataOverrides = listOf(
                bosituasjongrunnlagEnslig(
                    periode = Periode.create(1.januar(2021), 30.april(2021)),
                ),
                bosituasjongrunnlagEpsUførFlyktning(
                    periode = Periode.create(1.mai(2021), 31.desember(2021)),
                ),
            ),
        ).second

        BeregningStrategyFactory(
            clock = fixedClock,
        ).beregn(revurdering).let { beregning ->
            beregning.getMånedsberegninger().groupBy { it.getSats() }.let { satsMånedMap ->
                satsMånedMap shouldHaveSize 2
                satsMånedMap[Sats.HØY]!!.let { månedsberegninger ->
                    månedsberegninger.map { it.periode }.reduser().single() shouldBe Periode.create(
                        1.januar(2021),
                        30.april(2021),
                    )
                    månedsberegninger.all { it.getSumYtelse() == Sats.HØY.månedsbeløpSomHeltall(it.periode.fraOgMed) } shouldBe true
                    månedsberegninger.all { it.getSumFradrag() == 0.0 } shouldBe true
                    månedsberegninger.all { it.getFribeløpForEps() == 0.0 } shouldBe true
                    månedsberegninger.all { it.erFradragForEpsBenyttetIBeregning() } shouldBe false
                }

                satsMånedMap[Sats.ORDINÆR]!!.let { månedsberegninger ->
                    månedsberegninger.map { it.periode }.reduser().single() shouldBe Periode.create(
                        1.mai(2021),
                        31.desember(2021),
                    )
                    månedsberegninger.all { it.getSumYtelse() == Sats.ORDINÆR.månedsbeløpSomHeltall(it.periode.fraOgMed) } shouldBe true
                    månedsberegninger.all { it.getSumFradrag() == 0.0 } shouldBe true
                    månedsberegninger.all { it.getFribeløpForEps() == Sats.ORDINÆR.månedsbeløp(it.periode.fraOgMed) } shouldBe true
                    månedsberegninger.all { it.erFradragForEpsBenyttetIBeregning() } shouldBe false
                }
            }
        }
    }
}
