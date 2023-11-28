package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.bosituasjongrunnlagEpsUførFlyktning
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import satser.domain.Satskategori

internal class BeregningMedFlereSatserTest {
    @Test
    fun `kan beregne med flere forskjellig strategier for samme beregning`() {
        val revurdering = opprettetRevurdering(
            revurderingsperiode = år(2021),
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
            satsFactory = satsFactoryTestPåDato(),
        ).beregn(revurdering).let { beregning ->
            beregning.getMånedsberegninger().groupBy { it.getSats() }.let { satsMånedMap ->
                satsMånedMap shouldHaveSize 2
                satsMånedMap[Satskategori.HØY]!!.let { månedsberegninger ->
                    månedsberegninger.map { it.periode }.minsteAntallSammenhengendePerioder().single() shouldBe Periode.create(
                        1.januar(2021),
                        30.april(2021),
                    )
                    månedsberegninger.all { it.getSumYtelse() == satsFactoryTestPåDato().høyUføre(januar(2021)).satsForMånedAvrundet } shouldBe true
                    månedsberegninger.all { it.getSumFradrag() == 0.0 } shouldBe true
                    månedsberegninger.all { it.getFribeløpForEps() == 0.0 } shouldBe true
                    månedsberegninger.all { it.erFradragForEpsBenyttetIBeregning() } shouldBe false
                }

                satsMånedMap[Satskategori.ORDINÆR]!!.let { månedsberegninger ->
                    månedsberegninger.map { it.periode }.minsteAntallSammenhengendePerioder().single() shouldBe Periode.create(
                        1.mai(2021),
                        31.desember(2021),
                    )
                    månedsberegninger.all { it.getSumYtelse() == satsFactoryTestPåDato().ordinærUføre(mai(2021)).satsForMånedAvrundet } shouldBe true
                    månedsberegninger.all { it.getSumFradrag() == 0.0 } shouldBe true
                    månedsberegninger.all { it.getFribeløpForEps() == satsFactoryTestPåDato().ordinærUføre(mai(2021)).satsForMånedAsDouble } shouldBe true
                    månedsberegninger.all { it.erFradragForEpsBenyttetIBeregning() } shouldBe false
                }
            }
        }
    }
}
