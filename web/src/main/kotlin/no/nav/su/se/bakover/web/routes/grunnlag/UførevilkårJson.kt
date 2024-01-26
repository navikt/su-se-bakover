package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.uføre.UførevilkårStatus
import vilkår.common.domain.Vurdering
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.VurderingsperiodeUføre
import java.time.format.DateTimeFormatter

internal data class UføreVilkårJson(
    val vurderinger: List<VurderingsperiodeUføreJson>,
    val resultat: UførevilkårStatus,
)

internal fun UføreVilkår.toJson(): UføreVilkårJson? {
    return when (this) {
        UføreVilkår.IkkeVurdert -> null
        is UføreVilkår.Vurdert -> this.toJson()
    }
}

internal fun VurderingsperiodeUføre.toJson() = VurderingsperiodeUføreJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    resultat = vurdering.toUførhetStatusString(),
    grunnlag = grunnlag?.toJson(),
    periode = periode.toJson(),
)

internal fun UføreVilkår.Vurdert.toJson() = UføreVilkårJson(
    vurderinger = vurderingsperioder.map { it.toJson() },
    resultat = vurdering.toUførhetStatusString(),
)

internal fun Vurdering.toUførhetStatusString() = when (this) {
    Vurdering.Avslag -> UførevilkårStatus.VilkårIkkeOppfylt
    Vurdering.Innvilget -> UførevilkårStatus.VilkårOppfylt
    Vurdering.Uavklart -> UførevilkårStatus.HarUføresakTilBehandling
}

internal data class VurderingsperiodeUføreJson(
    val id: String,
    val opprettet: String,
    val resultat: UførevilkårStatus,
    val grunnlag: UføregrunnlagJson?,
    val periode: PeriodeJson,
)
