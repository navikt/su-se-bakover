package dokument.domain.generer.bygger

import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt
import kotlin.collections.filterNot
import kotlin.collections.isNotEmpty

data class BeregningsperiodeBrev(
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

fun List<BeregningsperiodeBrev>.harFradrag(): Boolean {
    return this.any {
        // TODO jah: Rest av Avkorting. Brukes kun fra brev. Det skal egentlig ikke finnes nye avkortingsfradrag i nye behandlinger. Undersøk om vi kan fjerne denne filtreringen.
        it.fradrag.bruker.filterNot { fradrag -> fradrag.type == "Avkorting på grunn av tidligere utenlandsopphold" }
            .isNotEmpty() ||
            it.fradrag.eps.fradrag.isNotEmpty()
    }
}

data class FradragForBrev(
    val bruker: List<MånedsfradragForBrev>,
    val eps: Eps,
) {
    data class Eps(
        val fradrag: List<MånedsfradragForBrev>,
        val harFradragMedSumSomErLavereEnnFribeløp: Boolean,
    )
}

data class MånedsfradragForBrev(
    val type: String,
    val beløp: Int,
    val utenlandskInntekt: UtenlandskInntekt?,
)

data class SatsoversiktBrev(
    val perioder: List<Satsperiode>,
) {
    data class Satsperiode(
        val fraOgMed: String,
        val tilOgMed: String,
        val sats: String,
        val satsBeløp: Int,
        val satsGrunn: String,
    )
}
