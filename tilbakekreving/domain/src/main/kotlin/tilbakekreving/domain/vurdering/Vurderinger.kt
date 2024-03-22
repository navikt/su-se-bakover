package tilbakekreving.domain.vurdering

import arrow.core.Nel
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall

/**
 * Krever at månedene er sorterte, uten duplikater, men vi aksepterer hull.
 *
 * @see [Vurdering] og [VurderCommand]
 */
data class Vurderinger(
    val perioder: Nel<Periodevurdering>,
) : List<Vurderinger.Periodevurdering> by perioder {
    init {
        perioder.map { it.periode }.let {
            require(it.map { it.fraOgMed }.sorted() == it.map { it.fraOgMed }) {
                "Vurderingene må være sortert på fraOgMed, men var: $it"
            }
            it.zipWithNext { a, b ->
                require(!a.overlapper(b)) {
                    "Perioder kan ikke overlappe, men var: $it"
                }
            }
        }
    }

    data class Periodevurdering(
        val periode: DatoIntervall,
        val vurdering: Vurdering,
    )
}
