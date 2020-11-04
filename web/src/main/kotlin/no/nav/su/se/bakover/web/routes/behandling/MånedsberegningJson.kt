package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import java.time.format.DateTimeFormatter

internal data class MånedsberegningJson(
    val fraOgMed: String,
    val tilOgMed: String,
    val sats: String,
    val grunnbeløp: Int,
    val beløp: Double
)
// TODO expand to show fradrag/mnd
internal fun Månedsberegning.toJson() = MånedsberegningJson(
    fraOgMed = periode().fraOgMed().format(DateTimeFormatter.ISO_DATE),
    tilOgMed = periode().tilOgMed().format(DateTimeFormatter.ISO_DATE),
    sats = sats().name,
    grunnbeløp = grunnbeløp(),
    beløp = sum()
)
