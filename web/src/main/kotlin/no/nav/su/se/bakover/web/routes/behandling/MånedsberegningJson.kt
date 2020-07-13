package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.MånedsberegningDto
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.web.Resultat
import java.time.format.DateTimeFormatter

internal data class MånedsberegningJson(
    val id: String,
    val fom: String,
    val tom: String,
    val sats: String,
    val grunnbeløp: Int,
    val beløp: Int
)

internal fun MånedsberegningDto.toJson() = MånedsberegningJson(
    id = id.toString(),
    fom = fom.format(DateTimeFormatter.ISO_DATE),
    tom = tom.format(DateTimeFormatter.ISO_DATE),
    sats = sats.name,
    grunnbeløp = grunnbeløp,
    beløp = beløp
)

internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<MånedsberegningDto>) =
    Resultat.json(this, serialize(dtoConvertable.toDto().toJson()))
