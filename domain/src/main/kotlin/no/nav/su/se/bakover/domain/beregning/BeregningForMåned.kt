package no.nav.su.se.bakover.domain.beregning

import beregning.domain.Merknad
import beregning.domain.Merknader
import beregning.domain.fradrag.FradragForMåned
import no.nav.su.se.bakover.common.tid.periode.Måned
import satser.domain.Satskategori
import satser.domain.supplerendestønad.FullSupplerendeStønadForMåned

/**
 * Gjelder for kun 1 måned.
 * [måned], [fradrag] og [fullSupplerendeStønadForMåned] sin måned må være den samme.
 *
 * TODO jah: Slett interfacet Månedsberegning og erstatt med dette.
 */
data class BeregningForMåned(
    override val måned: Måned,
    private val fradrag: List<FradragForMåned>,
    override val fullSupplerendeStønadForMåned: FullSupplerendeStønadForMåned,
    private val fribeløpForEps: Double = 0.0,
    private val merknader: Merknader.Beregningsmerknad = Merknader.Beregningsmerknad(),
    private val sumYtelse: Int,
    private val sumFradrag: Double,
) : Månedsberegning {

    override val periode: Måned = måned

    init {
        require(fradrag.all { it.måned == måned }) { "Fradrag må være gjeldende for aktuell måned" }
        require(periode == fullSupplerendeStønadForMåned.periode) {
            "perioden: $periode må være lik `fullSupplerendeStønadForMåned`: ${fullSupplerendeStønadForMåned.periode} sin periode."
        }
    }

    override fun getSumYtelse(): Int = sumYtelse
    override fun getSumFradrag() = sumFradrag

    /**
     * Obs: Denne returnerer grunnbeløp per år
     */
    override fun getBenyttetGrunnbeløp(): Int? = when (fullSupplerendeStønadForMåned) {
        is FullSupplerendeStønadForMåned.Alder -> {
            null
        }
        is FullSupplerendeStønadForMåned.Uføre -> {
            fullSupplerendeStønadForMåned.grunnbeløp.grunnbeløpPerÅr
        }
    }

    override fun getSats(): Satskategori = fullSupplerendeStønadForMåned.satskategori
    override fun getSatsbeløp(): Double = fullSupplerendeStønadForMåned.satsForMånedAsDouble
    override fun getFradrag(): List<FradragForMåned> = fradrag
    override fun getFribeløpForEps(): Double = fribeløpForEps

    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false

    override fun getMerknader(): List<Merknad.Beregning> {
        return merknader.alle()
    }

    fun leggTilMerknad(merknad: Merknad.Beregning) {
        merknader.leggTil(merknad)
    }

    fun beløpStørreEnn0MenMindreEnnToProsentAvHøySats(): Boolean {
        return getSumYtelse() > 0 && getSumYtelse() < fullSupplerendeStønadForMåned.toProsentAvHøyForMånedAsDouble
    }
}
