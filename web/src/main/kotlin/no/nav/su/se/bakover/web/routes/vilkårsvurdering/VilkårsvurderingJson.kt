package no.nav.su.se.bakover.web.routes.vilkårsvurdering

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.domain.VilkårsvurderingDto

internal data class VilkårsvurderingJson @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    @JsonValue val vilkårsvurderinger: Map<String, VilkårsvurderingData>
)

internal data class VilkårsvurderingData(
    val id: Long,
    val begrunnelse: String,
    val status: String
)

internal fun VilkårsvurderingDto.toJson() = vilkår.name to VilkårsvurderingData(
    id = id,
    begrunnelse = begrunnelse,
    status = status.name
)

internal fun Map<String, VilkårsvurderingData>.toVilkårsvurderinger() = this.map {
    Vilkårsvurdering(
        id = it.value.id,
        vilkår = Vilkår.valueOf(it.key),
        begrunnelse = it.value.begrunnelse,
        status = Vilkårsvurdering.Status.valueOf(it.value.status)
    )
}

internal fun List<VilkårsvurderingDto>.toJson() = VilkårsvurderingJson(this.map { it.toJson() }.toMap())
