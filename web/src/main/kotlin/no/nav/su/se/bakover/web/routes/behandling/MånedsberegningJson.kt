package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import java.time.format.DateTimeFormatter

internal data class MånedsberegningJson(
    val id: String,
    val fom: String,
    val fraOgMed: String,
    val tom: String,
    val tilOgMed: String,
    val sats: String,
    val grunnbeløp: Int,
    val beløp: Int
)

internal fun Månedsberegning.toJson() = MånedsberegningJson(
    id = id.toString(),
    fom = fraOgMed.format(DateTimeFormatter.ISO_DATE),
    fraOgMed = fraOgMed.format(DateTimeFormatter.ISO_DATE),
    tom = tilOgMed.format(DateTimeFormatter.ISO_DATE),
    tilOgMed = tilOgMed.format(DateTimeFormatter.ISO_DATE),
    sats = sats.name,
    grunnbeløp = grunnbeløp,
    beløp = beløp
)
