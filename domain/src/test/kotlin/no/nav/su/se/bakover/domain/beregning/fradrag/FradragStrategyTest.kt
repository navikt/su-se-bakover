package no.nav.su.se.bakover.domain.beregning.fradrag

import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class FradragStrategyTest {
    @Test
    fun `alle beregninger av fradrag må inneholde brukers forventede inntekt etter uførhet`() {
        assertThrows<IllegalArgumentException> {
            FradragStrategy.Enslig.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }.let {
            it.message shouldContain "Fradrag må inneholde brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsOver67År.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }.let {
            it.message shouldContain "Fradrag må inneholde brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }.let {
            it.message shouldContain "Fradrag må inneholde brukers forventede inntekt etter uførhet."
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67År.beregn(
                fradrag = listOf(lagFradrag(Fradragstype.Kontantstøtte, 5000.0))
            )
        }.let {
            it.message shouldContain "Fradrag må inneholde brukers forventede inntekt etter uførhet."
        }
    }

    @Test
    fun `alle beregnigner av fradrag krever at fradrag gjelder for en 12-månedersperiode`() {
        assertThrows<IllegalArgumentException> {
            FradragStrategy.Enslig.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.januar(2020), 31.januar(2020))
                    )
                )
            )
        }.let {
            it.message shouldContain "Beregning av fradrag støtter kun behandling av 12-måneders perioder"
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsOver67År.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.januar(2020), 31.oktober(2020))
                    )
                )
            )
        }.let {
            it.message shouldContain "Beregning av fradrag støtter kun behandling av 12-måneders perioder"
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.april(2020), 31.mai(2020))
                    )
                )
            )
        }.let {
            it.message shouldContain "Beregning av fradrag støtter kun behandling av 12-måneders perioder"
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67År.beregn(
                fradrag = listOf(
                    lagFradrag(
                        Fradragstype.ForventetInntekt,
                        5000.0,
                        Periode(1.september(2020), 31.desember(2020))
                    )
                )
            )
        }.let {
            it.message shouldContain "Beregning av fradrag støtter kun behandling av 12-måneders perioder"
        }
    }

    @Test
    fun `alle beregninger av fradrag krever at alle fradrag er innenfor samme 12-måneders periode`() {
        assertThrows<IllegalArgumentException> {
            FradragStrategy.Enslig.beregn(
                fradrag = listOf(
                    lagFradrag(Fradragstype.ForventetInntekt, 5000.0, Periode(1.januar(2020), 31.desember(2020))),
                    lagFradrag(Fradragstype.Kapitalinntekt, 5000.0, Periode(1.mai(2020), 30.april(2021)))
                )
            )
        }.let {
            it.message shouldContain "Beregning av fradrag støtter kun behandling av 12-måneders perioder"
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsOver67År.beregn(
                fradrag = listOf(
                    lagFradrag(Fradragstype.ForventetInntekt, 5000.0, Periode(1.januar(2020), 31.desember(2020))),
                    lagFradrag(Fradragstype.Kapitalinntekt, 5000.0, Periode(1.mai(2020), 30.april(2021)))
                )
            )
        }.let {
            it.message shouldContain "Beregning av fradrag støtter kun behandling av 12-måneders perioder"
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                fradrag = listOf(
                    lagFradrag(Fradragstype.ForventetInntekt, 5000.0, Periode(1.januar(2020), 31.desember(2020))),
                    lagFradrag(Fradragstype.Kapitalinntekt, 5000.0, Periode(1.mai(2020), 30.april(2021)))
                )
            )
        }.let {
            it.message shouldContain "Beregning av fradrag støtter kun behandling av 12-måneders perioder"
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67År.beregn(
                fradrag = listOf(
                    lagFradrag(Fradragstype.ForventetInntekt, 5000.0, Periode(1.januar(2020), 31.desember(2020))),
                    lagFradrag(Fradragstype.Kapitalinntekt, 5000.0, Periode(1.mai(2020), 30.april(2021)))
                )
            )
        }.let {
            it.message shouldContain "Beregning av fradrag støtter kun behandling av 12-måneders perioder"
        }
    }

    @Test
    fun `alle beregninger av fradrag krever at alle hvert enkelt fradrag har en periode på 12 måneder`() {
        assertThrows<IllegalArgumentException> {
            FradragStrategy.Enslig.beregn(
                fradrag = listOf(
                    lagFradrag(Fradragstype.ForventetInntekt, 5000.0, Periode(1.januar(2020), 31.desember(2020))),
                    lagFradrag(Fradragstype.Kapitalinntekt, 5000.0, Periode(1.mai(2020), 30.juni(2020)))
                )
            )
        }.let {
            it.message shouldContain "Alle fradrag må være oppgitt i 12-måneders perioder"
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsOver67År.beregn(
                fradrag = listOf(
                    lagFradrag(Fradragstype.ForventetInntekt, 5000.0, Periode(1.januar(2020), 31.desember(2020))),
                    lagFradrag(Fradragstype.Kapitalinntekt, 5000.0, Periode(1.mai(2020), 30.juni(2020)))
                )
            )
        }.let {
            it.message shouldContain "Alle fradrag må være oppgitt i 12-måneders perioder"
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67ÅrOgUførFlyktning.beregn(
                fradrag = listOf(
                    lagFradrag(Fradragstype.ForventetInntekt, 5000.0, Periode(1.januar(2020), 31.desember(2020))),
                    lagFradrag(Fradragstype.Kapitalinntekt, 5000.0, Periode(1.mai(2020), 30.juni(2020)))
                )
            )
        }.let {
            it.message shouldContain "Alle fradrag må være oppgitt i 12-måneders perioder"
        }
        assertThrows<IllegalArgumentException> {
            FradragStrategy.EpsUnder67År.beregn(
                fradrag = listOf(
                    lagFradrag(Fradragstype.ForventetInntekt, 5000.0, Periode(1.januar(2020), 31.desember(2020))),
                    lagFradrag(Fradragstype.Kapitalinntekt, 5000.0, Periode(1.mai(2020), 30.juni(2020)))
                )
            )
        }.let {
            it.message shouldContain "Alle fradrag må være oppgitt i 12-måneders perioder"
        }
    }
}

internal fun lagFradrag(
    type: Fradragstype,
    beløp: Double,
    periode: Periode = Periode(1.januar(2020), 31.desember(2020)),
    tilhører: FradragTilhører = FradragTilhører.BRUKER
) = FradragFactory.ny(
    type = type,
    beløp = beløp,
    periode = periode,
    utenlandskInntekt = null,
    tilhører = tilhører
)
