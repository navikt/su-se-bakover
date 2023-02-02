package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode

/**
 * Ment å kunne skape en tidslinje av vedtakene våre for å kunne si noe om en bruker har stønad en gitt måned.
 */
data class ForenkletVedtak(
    val opprettet: Tidspunkt,
    val periode: Periode,
    val fødselsnummer: Fnr,
    val vedtakstype: Vedtakstype,
) {
    init {
        if (vedtakstype == Vedtakstype.SØKNADSBEHANDLING) {
            require(periode.getAntallMåneder() <= 12)
        }
    }
}

/**
 * @return Sortert i stigende rekkefølge
 */
fun List<ForenkletVedtak>.tilInnvilgetForMåned(måned: Måned): InnvilgetForMåned {
    return this
        .filter { it.periode.overlapper(måned) }
        .groupBy { it.fødselsnummer }
        .mapNotNull {
            require(it.value.map { it.opprettet }.distinct().size == it.value.size) {
                "Forsikrer oss om at en sak ikke har flere vedtak som er opprettet samtidig."
            }
            if (it.value.maxByOrNull { it.opprettet.instant }!!.vedtakstype != Vedtakstype.REVURDERING_OPPHØR) {
                it.key
            } else {
                null
            }
        }.let {
            InnvilgetForMåned(måned, it.sortedBy { it.toString() })
        }
}
