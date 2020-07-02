package no.nav.su.se.bakover.web.routes.sak

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.SakDto
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.routes.stønadsperiode.StønadsperiodeJson
import no.nav.su.se.bakover.web.routes.stønadsperiode.toJson

internal data class SakJson(
    val id: Long,
    val fnr: String,
    val stønadsperioder: List<StønadsperiodeJson>
)

private fun SakDto.toJson() = SakJson(
    id = id,
    fnr = fnr.toString(),
    stønadsperioder = stønadsperioder.map { it.toJson() }
)

internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<SakDto>) =
    Resultat.json(this, objectMapper.writeValueAsString(dtoConvertable.toDto().toJson()))
