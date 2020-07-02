package no.nav.su.se.bakover.web.routes.stønadsperiode

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.StønadsperiodeDto
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.routes.behandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.behandling.toJson

internal data class StønadsperiodeJson(
    val id: Long,
    val søknad: SøknadJson,
    val behandlinger: List<BehandlingJson>
)

internal fun StønadsperiodeDto.toJson() = StønadsperiodeJson(
    id = id,
    søknad = søknad.toJson(),
    behandlinger = behandlinger.map { it.toJson() }
)

internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<StønadsperiodeDto>) =
    Resultat.json(this, objectMapper.writeValueAsString(dtoConvertable.toDto().toJson()))
