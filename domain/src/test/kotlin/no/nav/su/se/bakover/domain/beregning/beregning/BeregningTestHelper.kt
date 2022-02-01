package no.nav.su.se.bakover.domain.beregning.beregning

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.finnMånederMedMerknad

fun Beregning.finnMerknaderForPeriode(periode: Periode): List<Merknad.Beregning> {
    return finnMånederMedMerknad()
        .getOrHandle { return emptyList() }
        .let { månedsberegninger -> månedsberegninger.find { it.first.periode == periode }!!.second }
}
