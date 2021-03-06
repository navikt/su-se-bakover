package no.nav.su.se.bakover.domain.revurdering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
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
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000, Periode.create(1.januar(2020), 31.desember(2020)))),
            nyBeregning = lagBeregning(5000),
        ).resultat shouldBe true
    }

    @Test
    fun `en av månedene har endring fra tidligere gir true`() {
        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(
                Periode.create(1.januar(2021), 31.januar(2021)) to 7500,
                Periode.create(1.februar(2021), 28.februar(2021)) to 5000,
                Periode.create(1.mars(2021), 31.mars(2021)) to 5000,
                Periode.create(1.april(2021), 30.april(2021)) to 5000,
            ),
        ).resultat shouldBe true

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(
                Periode.create(1.januar(2021), 31.januar(2021)) to 5000,
                Periode.create(1.februar(2021), 28.februar(2021)) to 5000,
                Periode.create(1.mars(2021), 31.mars(2021)) to 5000,
                Periode.create(1.april(2021), 30.april(2021)) to 7500,
            ),
        ).resultat shouldBe true

        VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(
                Periode.create(1.januar(2021), 31.januar(2021)) to 5000,
                Periode.create(1.februar(2021), 28.februar(2021)) to 2500,
                Periode.create(1.mars(2021), 31.mars(2021)) to 5000,
                Periode.create(1.april(2021), 30.april(2021)) to 5000,
            ),
        ).resultat shouldBe true
    }

    private fun lagUtbetaling(månedsbeløp: Int, periode: Periode = beregningsperiode) = Utbetalingslinje.Ny(
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = null,
        beløp = månedsbeløp,
    )

    private fun lagBeregning(månedsbeløp: Int): Beregning {
        return lagBeregning(beregningsperiode to månedsbeløp)
    }

    private fun lagBeregning(vararg periodeBeløpMap: Pair<Periode, Int>): Beregning {
        val fradrag = periodeBeløpMap.map {
            val sats = Sats.HØY.månedsbeløp(it.first.fraOgMed)
            val diff = abs(sats - it.second)
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = diff,
                periode = it.first,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )
        }
        return BeregningFactory.ny(
            periode = periodeBeløpMap.map { it.first }
                .let { perioder -> Periode.create(perioder.minOf { it.fraOgMed }, perioder.maxOf { it.tilOgMed }) },
            sats = Sats.HØY,
            fradrag = fradrag,
            fradragStrategy = FradragStrategy.Enslig,
            begrunnelse = null,
        )
    }
}
