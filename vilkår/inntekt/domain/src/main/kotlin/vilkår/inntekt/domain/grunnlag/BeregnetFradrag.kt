package vilkår.inntekt.domain.grunnlag

import no.nav.su.se.bakover.common.domain.extensions.limitedUpwardsTo
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifsering
import no.nav.su.se.bakover.common.tid.periode.Måned

/*
 Regelspesifisering beregning av summert fradrag for en måned
*/
data class BeregnetFradrag(
    val fradragForMåned: BeregnetFradragForMåned,
    val sumFradrag: Double,
    override val benyttetRegel: MutableList<Regelspesifsering>,
) : RegelspesifisertBeregning {
    override fun leggTilbenyttetRegel(regel: Regelspesifsering): RegelspesifisertBeregning {
        throw NotImplementedError("Ikke relevant for klasse")
    }

    override fun leggTilbenyttetRegler(regler: List<Regelspesifsering>): BeregnetFradrag {
        benyttetRegel.addAll(regler)
        return this
    }

    companion object {
        fun create(fradragForMåned: BeregnetFradragForMåned, satsbeløp: Double) = BeregnetFradrag(
            fradragForMåned = fradragForMåned,
            sumFradrag = fradragForMåned.verdi.sum().limitedUpwardsTo(satsbeløp),
            benyttetRegel = mutableListOf(
                Regelspesifiseringer.REGEL_SAMLET_FRADRAG.benyttRegelspesifisering(),
            ),
        ).leggTilbenyttetRegler(fradragForMåned.benyttetRegel)
    }
}

/*
Vil bestå av fradrag som legges til av saksbehandler og basert på bosituasjon og div regelberegninger mutere innholdet.
*/
data class BeregnetFradragForMåned(
    val måned: Måned,
    val verdi: List<FradragForMåned>, // TODO istedenfor felt, heller extende List<FradragForMåned> ??
    override val benyttetRegel: MutableList<Regelspesifsering>,
) : RegelspesifisertBeregning {
    override fun leggTilbenyttetRegel(regel: Regelspesifsering): BeregnetFradragForMåned {
        benyttetRegel.add(regel)
        return this
    }

    override fun leggTilbenyttetRegler(regler: List<Regelspesifsering>): BeregnetFradragForMåned {
        benyttetRegel.addAll(regler)
        return this
    }

    fun nyBeregning(
        fradrag: List<FradragForMåned>,
        nyeRegler: List<Regelspesifsering>,
    ) = copy(
        verdi = fradrag,
    ).leggTilbenyttetRegler(nyeRegler)
}
