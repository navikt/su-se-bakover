package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.common.tid.periode.Periode

data class Beregningsperiode(
    val ytelsePerMåned: Int,
    val satsbeløpPerMåned: Int,
    val epsFribeløp: Int,
    val fradrag: Fradrag,
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
        it.fradrag.bruker.filterNot { fradrag -> fradrag.type == "Avkorting på grunn av tidligere utenlandsopphold" }
            .isNotEmpty() || it.fradrag.eps.fradrag.isNotEmpty()
    }
}

fun List<Beregningsperiode>.harAvkorting(): Boolean {
    return this.any { it.fradrag.bruker.any { fradrag -> fradrag.type == "Avkorting på grunn av tidligere utenlandsopphold" } }
}
