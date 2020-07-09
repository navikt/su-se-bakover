package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.BehandlingDto
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.søknad.SøknadJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.routes.vilkårsvurdering.VilkårsvurderingJson
import no.nav.su.se.bakover.web.routes.vilkårsvurdering.toJson
import no.nav.su.se.bakover.web.serialize

internal data class BehandlingJson(
    val id: String,
    val vilkårsvurderinger: VilkårsvurderingJson,
    val søknad: SøknadJson
)

internal fun BehandlingDto.toJson() = BehandlingJson(
    id = id.toString(),
    vilkårsvurderinger = vilkårsvurderinger.toJson(),
    søknad = søknad.toJson()
)

internal fun HttpStatusCode.jsonBody(dtoConvertable: DtoConvertable<BehandlingDto>) =
    Resultat.json(this, serialize(dtoConvertable.toDto().toJson()))

// internal fun serialize(dtoConvertable: DtoConvertable<BehandlingDto>) =
//     objectMapper.writeValueAsString(dtoConvertable.toDto().toJson())
