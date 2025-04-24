package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.common.tid.periode.Periode

data class Beregningsperiode(
    val ytelsePerMåned: Int,
    val satsbeløpPerMåned: Int,
    val epsFribeløp: Int,
    val fradrag: FradragForBrev,
    val periode: BrevPeriode,
    val sats: String,
)

data class BrevPeriode(
    val fraOgMed: String,
    val tilOgMed: String,
)

fun Periode.tilBrevperiode(): BrevPeriode {
    return BrevPeriode(
        fraOgMed = fraOgMed.ddMMyyyy(),
        tilOgMed = tilOgMed.ddMMyyyy(),
    )
}

fun List<Beregningsperiode>.harFradrag(): Boolean {
    return this.any {
        // TODO jah: Rest av Avkorting. Brukes kun fra brev. Det skal egentlig ikke finnes nye avkortingsfradrag i nye behandlinger. Undersøk om vi kan fjerne denne filtreringen.
        it.fradrag.bruker.filterNot { fradrag -> fradrag.type == "Avkorting på grunn av tidligere utenlandsopphold" }
            .isNotEmpty() ||
            it.fradrag.eps.fradrag.isNotEmpty()
    }
}
