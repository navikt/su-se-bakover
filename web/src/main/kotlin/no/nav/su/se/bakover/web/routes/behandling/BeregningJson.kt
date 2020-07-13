package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.BeregningDto
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.web.Resultat
import java.time.format.DateTimeFormatter

internal data class BeregningJson(
    val id: String,
    val fom: String,
    val tom: String,
    val sats: String,
    val månedsberegninger: List<MånedsberegningJson> = emptyList()
)

internal fun BeregningDto.toJson() = BeregningJson(
    id = id.toString(),
    fom = fom.format(DateTimeFormatter.ISO_DATE),
    tom = tom.format(DateTimeFormatter.ISO_DATE),
    sats = sats.name,
    månedsberegninger = månedsberegninger.map { it.toJson() }
)

internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<BeregningDto>) =
    Resultat.json(this, serialize(dtoConvertable.toDto().toJson()))
