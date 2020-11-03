package no.nav.su.se.bakover.web.routes.behandling

internal data class MånedsberegningJson(
    val id: String,
    val fraOgMed: String,
    val tilOgMed: String,
    val sats: String,
    val grunnbeløp: Int,
    val beløp: Int
)

internal fun Månedsberegning.toJson() = MånedsberegningJson(
    id = id.toString(),
    fraOgMed = fraOgMed.format(DateTimeFormatter.ISO_DATE),
    tilOgMed = tilOgMed.format(DateTimeFormatter.ISO_DATE),
    sats = sats.name,
    grunnbeløp = grunnbeløp,
    beløp = beløp
)
