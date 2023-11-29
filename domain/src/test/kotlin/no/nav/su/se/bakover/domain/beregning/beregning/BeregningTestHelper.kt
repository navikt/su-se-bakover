package no.nav.su.se.bakover.domain.beregning.beregning

import arrow.core.getOrElse
import beregning.domain.Merknad
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.finnMånederMedMerknad

fun Beregning.finnMerknaderForPeriode(periode: Periode): List<Merknad.Beregning> {
    return finnMånederMedMerknad()
        .getOrElse { return emptyList() }
        .let { månedsberegninger -> månedsberegninger.find { it.first.periode == periode }!!.second }
}
