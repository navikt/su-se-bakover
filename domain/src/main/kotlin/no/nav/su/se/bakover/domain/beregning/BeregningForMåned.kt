package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned

/**
 * Gjelder for kun 1 måned. TODO jah: Slett interfacet Månedsberegning og erstatt med dette.
 */
data class BeregningForMåned(
    override val måned: Månedsperiode,
    private val sats: Sats,
    private val fradrag: List<FradragForMåned>,
    private val fribeløpForEps: Double = 0.0,
    private val merknader: Merknader.Beregningsmerknad = Merknader.Beregningsmerknad(),
    private val sumYtelse: Int,
    private val sumFradrag: Double,
    private val satsbeløp: Double,
) : Månedsberegning {

    override val periode: Månedsperiode = måned
    init {
        require(fradrag.all { it.periode == periode }) { "Fradrag må være gjeldende for aktuell måned" }
    }

    override fun getSumYtelse(): Int = sumYtelse

    override fun getSumFradrag() = sumFradrag

    override fun getBenyttetGrunnbeløp(): Int = Grunnbeløp.`1G`.heltallPåDato(periode.fraOgMed)
    override fun getSats(): Sats = sats
    override fun getSatsbeløp(): Double = satsbeløp
    override fun getFradrag(): List<Fradrag> = fradrag
    override fun getFribeløpForEps(): Double = fribeløpForEps

    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false

    override fun getMerknader(): List<Merknad.Beregning> {
        return merknader.alle()
    }

    fun leggTilMerknad(merknad: Merknad.Beregning) {
        merknader.leggTil(merknad)
    }

    fun beløpStørreEnn0MenMindreEnnToProsentAvHøySats(): Boolean {
        return getSumYtelse() > 0 && getSumYtelse() < Sats.toProsentAvHøy(periode)
    }
}
