package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.BehandlingDto
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.routes.vilkårsvurdering.VilkårsvurderingJson
import no.nav.su.se.bakover.web.routes.vilkårsvurdering.toJson

internal data class BehandlingJson(
    val id: Long,
    val vilkårsvurderinger: VilkårsvurderingJson
)

internal fun BehandlingDto.toJson() = BehandlingJson(
    id = id,
    vilkårsvurderinger = vilkårsvurderinger.toJson()
)

internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<BehandlingDto>) =
    Resultat.json(this, objectMapper.writeValueAsString(dtoConvertable.toDto().toJson()))
