package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.limitedUpwardsTo
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.positiveOrZero
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import kotlin.math.roundToInt

internal data class PeriodisertBeregning(
    override val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>,
    private val fribeløpForEps: Double = 0.0,
) : Månedsberegning {
    private val merknader: Merknader.Beregningsmerknad = Merknader.Beregningsmerknad()

    init {
        require(fradrag.all { it.periode == periode }) { "Fradrag må være gjeldende for aktuell måned" }
        require(periode.getAntallMåneder() == 1) { "Månedsberegning kan kun utføres for en enkelt måned" }

        leggTilMerknadVedEndringIGrunnbeløp()
    }

    override fun getSumYtelse(): Int = (getSatsbeløp() - getSumFradrag())
        .positiveOrZero()
        .roundToInt()

    override fun getSumFradrag() = beregnSumFradrag(fradrag)

    override fun getBenyttetGrunnbeløp(): Int = Grunnbeløp.`1G`.heltallPåDato(periode.fraOgMed)
    override fun getSats(): Sats = sats
    override fun getSatsbeløp(): Double = sats.periodiser(periode).getValue(periode)
    override fun getFradrag(): List<Fradrag> = fradrag
    override fun getFribeløpForEps(): Double = fribeløpForEps

    private fun beregnSumFradrag(fradrag: List<Fradrag>) =
        fradrag.sumOf { it.månedsbeløp }.limitedUpwardsTo(getSatsbeløp())

    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false

    override fun getMerknader(): List<Merknad.Beregning> {
        return merknader.alle()
    }

    private fun leggTilMerknadVedEndringIGrunnbeløp() {
        Grunnbeløp.`1G`.let {
            if (periode.fraOgMed == it.datoForSisteEndringAvGrunnbeløp(periode.fraOgMed)) {
                leggTilMerknad(
                    Merknad.Beregning.EndringGrunnbeløp(
                        gammeltGrunnbeløp = Merknad.Beregning.EndringGrunnbeløp.Detalj.forDato(
                            it.datoForSisteEndringAvGrunnbeløp(periode.forskyv(-1).fraOgMed),
                        ),
                        nyttGrunnbeløp = Merknad.Beregning.EndringGrunnbeløp.Detalj.forDato(periode.fraOgMed),
                    ),
                )
            }
        }
    }

    fun leggTilMerknad(merknad: Merknad.Beregning) {
        merknader.leggTil(merknad)
    }

    fun beløpStørreEnn0MenMindreEnnToProsentAvHøySats(): Boolean {
        return getSumYtelse() > 0 && getSumYtelse() < Sats.toProsentAvHøy(periode)
    }
}
