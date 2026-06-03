package no.nav.su.se.bakover.domain.regulering

import beregning.domain.Beregning
import beregning.domain.Månedsberegning
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import satser.domain.SatsFactory
import satser.domain.supplerendestønad.FullSupplerendeStønadForMåned

/**
 * Sjekker om beregningen er gjort med et utdatert grunnbeløp (uføre) eller utdatert garantipensjon (alder)
 * sammenlignet med satsene som gjelder i dag ([satsFactory]).
 *
 * Hensikten er å fange opp behandlinger som ble beregnet før en regulering ble innført i systemet, slik at
 * de ikke kan iverksettes eller sendes til attestering med utdaterte satser for perioder som dekkes av den nye
 * reguleringens virkningstidspunkt.
 *
 * Sammenligningen gjenbruker [erRegulertMedNyttGrunnbeløp] per månedsberegning og er fremoverrettet av natur:
 * for måneder bakover i tid er benyttet grunnbeløp/sats lik dagens sats for samme måned, så de gir aldri treff.
 * Kun måneder fra og med en ny regulerings virkningstidspunkt vil kunne flagges som utdatert.
 */
fun Beregning.harUtdatertGrunnbeløp(satsFactory: SatsFactory): Boolean =
    getMånedsberegninger().any { it.harUtdatertGrunnbeløp(satsFactory) }

fun Månedsberegning.harUtdatertGrunnbeløp(satsFactory: SatsFactory): Boolean {
    if (måned < satsFactory.tidligsteTilgjengeligeMåned) return false
    val sakstype = when (fullSupplerendeStønadForMåned) {
        is FullSupplerendeStønadForMåned.Uføre -> Sakstype.UFØRE
        is FullSupplerendeStønadForMåned.Alder -> Sakstype.ALDER
    }
    return !satsFactory.grunnbeløpOgGarantipensjon(måned).erRegulertMedNyttGrunnbeløp(sakstype, this)
}
