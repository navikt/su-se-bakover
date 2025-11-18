package vilkår.inntekt.domain.grunnlag

import no.nav.su.se.bakover.common.domain.extensions.limitedUpwardsTo
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifsering

/*
 Regelspesifisering beregning av summert fradrag for en måned
*/
data class BeregnetFradrag(
    val fradragForMåned: List<FradragForMåned>,
    val sumFradrag: Double,
    override val benyttetRegel: MutableList<Regelspesifsering>,
) : RegelspesifisertBeregning {
    override fun leggTilbenyttetRegel(regel: Regelspesifsering): RegelspesifisertBeregning {
        throw NotImplementedError("Ikke relevant for klasse")
    }

    override fun leggTilbenyttetRegler(regler: List<Regelspesifsering>): RegelspesifisertBeregning {
        throw NotImplementedError("Ikke relevant for klasse")
    }

    companion object {
        fun create(fradragForMåned: List<FradragForMåned>, satsbeløp: Double) = BeregnetFradrag(
            fradragForMåned = fradragForMåned,
            sumFradrag = fradragForMåned.sum().limitedUpwardsTo(satsbeløp),
            benyttetRegel = mutableListOf(Regelspesifiseringer.REGEL_SAMLET_FRADRAG.benyttRegelspesifisering()),
        )
    }
}
