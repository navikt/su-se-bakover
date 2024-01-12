package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import java.util.UUID

/**
 * Ment å kunne skape en enkel tidslinje av vedtakene våre for å kunne si noe om en bruker har stønad en gitt måned.
 */
data class Vedtaksammendrag(
    val opprettet: Tidspunkt,
    val periode: Periode,
    val fødselsnummer: Fnr,
    val vedtakstype: Vedtakstype,
    val sakId: UUID,
    val saksnummer: Saksnummer,
) {
    init {
        if (vedtakstype == Vedtakstype.SØKNADSBEHANDLING_INNVILGELSE) {
            require(periode.getAntallMåneder() <= 12)
        }
    }
}

/**
 * @return Sortert i stigende rekkefølge for fnr
 */
fun List<Vedtaksammendrag>.tilInnvilgetForMåned(måned: Måned): InnvilgetForMåned {
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

/**
 * @return Sortert etter saksnummer i stigende rekkefølge
 */
fun List<Vedtaksammendrag>.tilInnvilgetForMånedEllerSenere(
    fraOgMedEllerSenere: Måned,
): InnvilgetForMånedEllerSenere {
    return this
        .filter { it.periode >= fraOgMedEllerSenere }
        .groupBy { it.fødselsnummer }
        .mapNotNull {
            require(it.value.map { it.opprettet }.distinct().size == it.value.size) {
                "Forsikrer oss om at en sak ikke har flere vedtak som er opprettet samtidig."
            }
            require(it.value.map { it.sakId }.distinct().size == 1) {
                // TODO jah: Denne blir feil hvis vi implementerer alder
                "Forsikrer oss om at et fødselsnummer kun er knyttet til én sakId."
            }
            require(it.value.map { it.saksnummer }.distinct().size == 1) {
                // TODO jah: Denne blir feil hvis vi implementerer alder
                "Forsikrer oss om at et fødselsnummer kun er knyttet til et saksnummer."
            }
            if (it.value.maxByOrNull { it.opprettet.instant }!!.vedtakstype != Vedtakstype.REVURDERING_OPPHØR) {
                SakInfo(
                    sakId = it.value.first().sakId,
                    saksnummer = it.value.first().saksnummer,
                    fnr = it.key,
                    // TODO jah: Denne blir feil hvis vi implementerer alder
                    type = Sakstype.UFØRE,
                )
            } else {
                null
            }
        }.let {
            InnvilgetForMånedEllerSenere(fraOgMedEllerSenere, it.sortedBy { it.saksnummer.toString() })
        }
}
