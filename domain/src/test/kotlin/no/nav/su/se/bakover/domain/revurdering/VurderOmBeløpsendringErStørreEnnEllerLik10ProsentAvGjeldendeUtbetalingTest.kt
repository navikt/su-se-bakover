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
import java.time.LocalDate
import kotlin.math.abs

internal class VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetalingTest {

    private val beregningsperiode = Periode.create(1.januar(2021), 30.april(2021))

    @Test
    fun `ingen eksisterende utbetalinger gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = emptyList(),
            nyBeregning = lagBeregning(5000),
        ).resultat shouldBe true
    }

    @Test
    fun `ingen utbetalinger overlapper med bergningsperioden gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(
                lagUtbetaling(5000, Periode.create(1.desember(2020), 31.desember(2020))),
                lagUtbetaling(5000, Periode.create(1.desember(2021), 31.desember(2021))),
            ),
            nyBeregning = lagBeregning(5000),
        ).resultat shouldBe true
    }

    @Test
    fun `alle måneder i ny beregning har endring større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(10000),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(1000),
        ).resultat shouldBe true
    }

    @Test
    fun `alle måneder i ny beregning har endring lik 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(5500),
        ).resultat shouldBe true

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(4500),
        ).resultat shouldBe true
    }

    @Test
    fun `ingen måneder i ny beregning har endring større enn 10 prosent gir false`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(5250),
        ).resultat shouldBe false

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(4750),
        ).resultat shouldBe false

        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(5000),
        ).resultat shouldBe false
    }

    @Test
    fun `alle måneder bortsett fra første har endring større enn 10 prosent gir false`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(
                Periode.create(1.januar(2021), 31.januar(2021)) to 5000,
                Periode.create(1.februar(2021), 28.februar(2021)) to 10000,
                Periode.create(1.mars(2021), 31.mars(2021)) to 1000,
                Periode.create(1.april(2021), 30.april(2021)) to 20000,
            ),
        ).resultat shouldBe false
    }

    @Test
    fun `alle måneder bortsett fra første har ikke endring større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(5000)),
            nyBeregning = lagBeregning(
                Periode.create(1.januar(2021), 31.januar(2021)) to 15000,
                Periode.create(1.februar(2021), 28.februar(2021)) to 5000,
                Periode.create(1.mars(2021), 31.mars(2021)) to 5000,
                Periode.create(1.april(2021), 30.april(2021)) to 5000,
            ),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er opphør og endringer tidligere enn opphørsdato er mindre enn 10 prosent gir false`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagOpphør(5000, 1.februar(2021))),
            nyBeregning = lagBeregning(5000),
        ).resultat shouldBe false
    }

    @Test
    fun `gjeldende utbetaling er opphør og endringer tidligere enn opphørsdato er større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagOpphør(5000, 1.februar(2021))),
            nyBeregning = lagBeregning(15000),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er opphør og endringer på opphørsdato er mindre enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagOpphør(5000, 1.februar(2021))),
            nyBeregning = lagBeregning(
                Periode.create(1.februar(2021), beregningsperiode.tilOgMed) to 5000,
            ),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er opphør og endringer på opphørsdato er større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagOpphør(5000, 1.februar(2021))),
            nyBeregning = lagBeregning(
                Periode.create(1.februar(2021), beregningsperiode.tilOgMed) to 15000,
            ),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er opphør og endringer senere enn opphørsdato er mindre enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagOpphør(5000, 1.februar(2021))),
            nyBeregning = lagBeregning(
                Periode.create(1.mars(2021), beregningsperiode.tilOgMed) to 5000,
            ),
        ).resultat shouldBe true
    }

    @Test
    fun `gjeldende utbetaling er opphør og endringer senere enn opphørsdato større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagOpphør(5000, 1.februar(2021))),
            nyBeregning = lagBeregning(
                Periode.create(1.mars(2021), beregningsperiode.tilOgMed) to 15000,
            ),
        ).resultat shouldBe true
    }

    @Test
    fun `ny beregning er under minstegrense for utbetaling, men differanse mot gjeldende utebetaling er mindre enn 10 prosent gir false`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(440)),
            nyBeregning = lagBeregning(405),
        ).resultat shouldBe false
    }

    @Test
    fun `ny beregning er under minstegrense for utbetaling, og differanse mot gjeldende utebetaling er større enn 10 prosent gir true`() {
        VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
            eksisterendeUtbetalinger = listOf(lagUtbetaling(440)),
            nyBeregning = lagBeregning(390),
        ).resultat shouldBe true
    }

    private fun lagUtbetaling(månedsbeløp: Int, periode: Periode = beregningsperiode) = Utbetalingslinje.Ny(
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        forrigeUtbetalingslinjeId = null,
        beløp = månedsbeløp,
    )

    private fun lagOpphør(månedsbeløp: Int, opphørsdato: LocalDate, periode: Periode = beregningsperiode) =
        Utbetalingslinje.Endring(
            utbetalingslinje = lagUtbetaling(månedsbeløp = månedsbeløp, periode = periode),
            statusendring = Utbetalingslinje.Statusendring(
                status = Utbetalingslinje.LinjeStatus.OPPHØR,
                fraOgMed = opphørsdato,
            ),
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
