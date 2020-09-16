package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import java.time.format.DateTimeFormatter

internal data class MånedsberegningJson(
    val id: String,
    val fom: String,
    val tom: String,
    val sats: String,
    val grunnbeløp: Int,
    val beløp: Int
)

internal fun Månedsberegning.toJson() = MånedsberegningJson(
    id = id.toString(),
    fom = fom.format(DateTimeFormatter.ISO_DATE),
    tom = tom.format(DateTimeFormatter.ISO_DATE),
    sats = sats.name,
    grunnbeløp = grunnbeløp,
    beløp = beløp
)
