package no.nav.su.se.bakover.web.routes.sak

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.SakDto
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.routes.behandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.behandling.toJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknad.toJson

internal data class SakJson(
    val id: String,
    val fnr: String,
    val søknader: List<SøknadJson>,
    val behandlinger: List<BehandlingJson>
)

internal fun SakDto.toJson() = SakJson(
    id = id.toString(),
    fnr = fnr.toString(),
    søknader = søknader.map { it.toJson() },
    behandlinger = behandlinger.map { it.toJson() }
)

internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<SakDto>) =
    Resultat.json(this, objectMapper.writeValueAsString(dtoConvertable.toDto().toJson()))
