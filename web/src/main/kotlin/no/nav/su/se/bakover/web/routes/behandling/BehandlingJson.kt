package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.BehandlingDto
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.routes.stønadsperiode.VilkårsvurderingData

internal data class BehandlingJson(
    val id: Long,
    val vilkårsvurderinger: Map<String, VilkårsvurderingData>
)

internal fun BehandlingDto.toJson() = BehandlingJson(
    id,
    vilkårsvurderinger.map {
        it.vilkår.name to VilkårsvurderingData(
            it.id,
            it.begrunnelse,
            it.status.name
        )
    }.toMap()
)

internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<BehandlingDto>) =
    Resultat.json(this, objectMapper.writeValueAsString(dtoConvertable.toDto().toJson()))
