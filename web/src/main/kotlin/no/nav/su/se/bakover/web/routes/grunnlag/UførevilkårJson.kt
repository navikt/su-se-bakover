package no.nav.su.se.bakover.web.routes.grunnlag

import common.presentation.grunnlag.UføregrunnlagJson
import common.presentation.grunnlag.toJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.domain.vilkår.uføre.UførevilkårStatus
import vilkår.common.domain.Vurdering
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.VurderingsperiodeUføre
import java.time.format.DateTimeFormatter

data class UføreVilkårJson(
    val vurderinger: List<VurderingsperiodeUføreJson>,
    val resultat: UførevilkårStatus,
)

fun UføreVilkår.toJson(): UføreVilkårJson? {
    return when (this) {
        UføreVilkår.IkkeVurdert -> null
        is UføreVilkår.Vurdert -> this.toJson()
    }
}

fun VurderingsperiodeUføre.toJson() = VurderingsperiodeUføreJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    resultat = vurdering.toUførhetStatusString(),
    grunnlag = grunnlag?.toJson(),
    periode = periode.toJson(),
)

fun UføreVilkår.Vurdert.toJson() = UføreVilkårJson(
    vurderinger = vurderingsperioder.map { it.toJson() },
    resultat = vurdering.toUførhetStatusString(),
)

fun Vurdering.toUførhetStatusString() = when (this) {
    Vurdering.Avslag -> UførevilkårStatus.VilkårIkkeOppfylt
    Vurdering.Innvilget -> UførevilkårStatus.VilkårOppfylt
    Vurdering.Uavklart -> UførevilkårStatus.HarUføresakTilBehandling
}

data class VurderingsperiodeUføreJson(
    val id: String,
    val opprettet: String,
    val resultat: UførevilkårStatus,
    val grunnlag: UføregrunnlagJson?,
    val periode: PeriodeJson,
)
