package beregning.domain

import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifsering
import no.nav.su.se.bakover.common.tid.periode.Måned
import satser.domain.Satskategori
import satser.domain.supplerendestønad.FullSupplerendeStønadForMåned
import vilkår.inntekt.domain.grunnlag.FradragForMåned

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
) : Månedsberegning,
    RegelspesifisertBeregning {

    override val periode: Måned = måned

    override val benyttetRegel: MutableList<Regelspesifsering> = mutableListOf()
    override fun leggTilbenyttetRegel(regel: Regelspesifsering): BeregningForMåned {
        benyttetRegel.add(regel)
        return this
    }
    fun leggTilbenyttetRegler(regler: List<Regelspesifsering>): BeregningForMåned {
        benyttetRegel.addAll(regler)
        return this
    }

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

    override fun getBenyttetRegler(): List<Regelspesifsering> {
        return this.benyttetRegel
    }

    fun leggTilMerknad(merknad: Merknad.Beregning) {
        merknader.leggTil(merknad)
    }

    // TODO bjg wrapper for regel??
    fun beløpStørreEnn0MenMindreEnnToProsentAvHøySats(): Boolean {
        return getSumYtelse() > 0 && getSumYtelse() < fullSupplerendeStønadForMåned.toProsentAvHøyForMånedAsDouble
    }
}
