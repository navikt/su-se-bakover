package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.limitedUpwardsTo
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.positiveOrZero
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.PeriodisertFradrag
import kotlin.math.roundToInt

internal data class PeriodisertBeregning(
    override val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<PeriodisertFradrag>,
    private val fribeløpForEps: Double = 0.0,
) : Månedsberegning {
    private val merknader: MutableList<Merknad> = mutableListOf()

    init {
        require(fradrag.all { it.periode == periode }) { "Fradrag må være gjeldende for aktuell måned" }
        require(periode.getAntallMåneder() == 1) { "Månedsberegning kan kun utføres for en enkelt måned" }

        leggTilMerknadVedEndringIGrunnbeløp()
    }

    override fun getSumYtelse(): Int = (getSatsbeløp() - getSumFradrag())
        .positiveOrZero()
        .roundToInt()

    override fun getSumFradrag() = fradrag
        .sumOf { it.månedsbeløp }
        .limitedUpwardsTo(getSatsbeløp())

    override fun getBenyttetGrunnbeløp(): Int = Grunnbeløp.`1G`.heltallPåDato(periode.fraOgMed)
    override fun getSats(): Sats = sats
    override fun getSatsbeløp(): Double = sats.periodiser(periode).getValue(periode)
    override fun getFradrag(): List<PeriodisertFradrag> = fradrag
    override fun getFribeløpForEps(): Double = fribeløpForEps

    override fun getMerknader(): MutableList<Merknad> {
        return merknader
    }

    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false

    internal fun forskyv(måneder: Int, fradragStrategy: FradragStrategy): PeriodisertBeregning {
        return periode.forskyv(måneder).let { nyPeriode ->
            copy(
                periode = nyPeriode,
                sats = sats,
                fradrag = fradrag.map { it.forskyv(måneder) },
                fribeløpForEps = fradragStrategy.getEpsFribeløp(nyPeriode),
            )
        }
    }

    private fun leggTilMerknadVedEndringIGrunnbeløp() {
        Grunnbeløp.`1G`.let {
            if (periode.fraOgMed == it.datoForSisteEndringAvGrunnbeløp(periode.fraOgMed)) {
                merknader.add(
                    Merknad.EndringGrunnbeløp(
                        gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj.forDato(
                            it.datoForSisteEndringAvGrunnbeløp(periode.forskyv(-1).fraOgMed),
                        ),
                        nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj.forDato(periode.fraOgMed),
                    ),
                )
            }
        }
    }
}
