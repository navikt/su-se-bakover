package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import java.time.format.DateTimeFormatter

internal data class MånedsberegningJson(
    val fraOgMed: String,
    val tilOgMed: String,
    val sats: String,
    val grunnbeløp: Int,
    val beløp: Int,
    val fradrag: List<FradragJson>
)
internal fun Månedsberegning.toJson() = MånedsberegningJson(
    fraOgMed = getPeriode().getFraOgMed().format(DateTimeFormatter.ISO_DATE),
    tilOgMed = getPeriode().getTilOgMed().format(DateTimeFormatter.ISO_DATE),
    sats = getSats().name,
    grunnbeløp = getBenyttetGrunnbeløp(),
    beløp = getSumYtelse(),
    fradrag = getFradrag().map { it.toJson() }
)
