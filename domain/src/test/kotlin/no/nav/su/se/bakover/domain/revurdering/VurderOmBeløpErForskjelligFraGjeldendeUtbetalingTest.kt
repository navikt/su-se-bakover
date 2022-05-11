package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.satsFactoryTest
import org.junit.jupiter.api.Test
import kotlin.math.abs

internal class VurderOmBeløpErForskjelligFraGjeldendeUtbetalingTest {

    private val beregningsperiode = Periode.create(1.januar(2021), 30.april(2021))

    @Test
    fun `ingen utbetalinger gir true`() {
        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = emptyList(),
            nyBeregning = lagBeregning(5000),
        ).resultat shouldBe true
    }

    @Test
    fun `alle måneder med samme beløp som før gir false`() {
        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(5000),
        ).resultat shouldBe false
    }

    @Test
    fun `alle måneder med annet beløp enn tidligere gir true`() {
        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(7500),
        ).resultat shouldBe true
    }

    @Test
    fun `utbetalinger overlapper ikke med beregning gir true`() {
        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000, år(2020))),
            nyBeregning = lagBeregning(5000),
        ).resultat shouldBe true
    }

    @Test
    fun `en av månedene har endring fra tidligere gir true`() {
        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(
                januar(2021) to 7500,
                februar(2021) to 5000,
                mars(2021) to 5000,
                april(2021) to 5000,
            ),
        ).resultat shouldBe true

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(
                januar(2021) to 5000,
                februar(2021) to 5000,
                mars(2021) to 5000,
                april(2021) to 7500,
            ),
        ).resultat shouldBe true

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(
                januar(2021) to 5000,
                februar(2021) to 2500,
                mars(2021) to 5000,
                april(2021) to 5000,
            ),
        ).resultat shouldBe true
    }

    private fun lagUtbetaling(månedsbeløp: Int, periode: Periode = beregningsperiode) = Utbetalingslinje.Ny(
        opprettet = fixedTidspunkt,
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = null,
        beløp = månedsbeløp,
        uføregrad = Uføregrad.parse(50),
    )

    private fun lagBeregning(månedsbeløp: Int): Beregning {
        return lagBeregning(beregningsperiode to månedsbeløp)
    }

    private fun lagBeregning(vararg periodeBeløpMap: Pair<Periode, Int>): Beregning {
        val fradrag = periodeBeløpMap.map {
            val sats: Double = satsFactoryTest.høy(it.first.måneder().head).satsForMånedAsDouble
            val diff: Double = abs(sats - it.second)
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = diff,
                periode = it.first,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )
        }
        val periode = periodeBeløpMap.map { it.first }
            .let { perioder -> Periode.create(perioder.minOf { it.fraOgMed }, perioder.maxOf { it.tilOgMed }) }
        return BeregningFactory(clock = fixedClock).ny(
            fradrag = fradrag,
            begrunnelse = null,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTest),
                ),
            ),
        )
    }
}
