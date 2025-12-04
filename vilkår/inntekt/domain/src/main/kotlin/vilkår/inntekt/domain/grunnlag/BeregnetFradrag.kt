package vilkår.inntekt.domain.grunnlag

import no.nav.su.se.bakover.common.domain.extensions.limitedUpwardsTo
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.tid.periode.Måned

/*
 Regelspesifisering beregning av summert fradrag for en måned
*/
data class BeregnetFradrag(
    val fradragForMåned: BeregnetFradragForMåned,
    val sumFradrag: Double,
    override val benyttetRegel: Regelspesifisering,
) : RegelspesifisertBeregning {
    companion object {
        fun create(fradragForMåned: BeregnetFradragForMåned, satsbeløp: Double): BeregnetFradrag {
            val verdi = fradragForMåned.verdi.sum().limitedUpwardsTo(satsbeløp)
            return BeregnetFradrag(
                fradragForMåned = fradragForMåned,
                sumFradrag = verdi,
                benyttetRegel = Regelspesifiseringer.REGEL_SAMLET_FRADRAG.benyttRegelspesifisering(
                    verdi = verdi.toString(),
                    avhengigeRegler = listOf(
                        fradragForMåned.benyttetRegel,
                    ),
                ),
            )
        }
    }
}

/*
Vil bestå av fradrag som legges til av saksbehandler og basert på bosituasjon og div regelberegninger mutere innholdet.
*/
data class BeregnetFradragForMåned(
    val måned: Måned,
    val verdi: List<FradragForMåned>,
    override val benyttetRegel: Regelspesifisering,
) : RegelspesifisertBeregning
