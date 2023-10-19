package tilbakekreving.domain.vurdert

import arrow.core.Nel
import no.nav.su.se.bakover.common.tid.periode.Måned

/**
 * Krever at månedene er sorterte, uten duplikater, men vi aksepterer hull.
 */
data class Vurderinger(
    val vurderinger: Nel<Månedsvurdering>,
) : List<Månedsvurdering> by vurderinger {
    init {
        vurderinger.map { it.måned }.let {
            require(it.distinct() == it) {
                "Kan bare vurdere en måned en gang."
            }
            require(it.sorted() == it) {
                "Vurderingene må være sortert etter måned."
            }
        }
    }
}

data class Månedsvurdering(
    val måned: Måned,
    val vurdering: Vurdering,
)
