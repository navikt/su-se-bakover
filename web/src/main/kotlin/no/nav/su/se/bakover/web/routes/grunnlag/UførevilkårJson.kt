package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import java.time.format.DateTimeFormatter

internal data class UføreVilkårJson(
    val vurderinger: List<VurderingsperiodeUføreJson>,
    val resultat: UførevilkårStatus,
)

internal fun Vurderingsperiode.Uføre.toJson() = VurderingsperiodeUføreJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    resultat = resultat.toUførhetStatusString(),
    grunnlag = grunnlag?.toJson(),
    periode = periode.toJson(),
    begrunnelse = begrunnelse,
)

internal fun Vilkår.Uførhet.Vurdert.toJson() = UføreVilkårJson(
    vurderinger = vurderingsperioder.map { it.toJson() },
    resultat = resultat.toUførhetStatusString(),
)

internal fun Resultat.toUførhetStatusString() = when (this) {
    Resultat.Avslag -> UførevilkårStatus.VilkårIkkeOppfylt
    Resultat.Innvilget -> UførevilkårStatus.VilkårOppfylt
    Resultat.Uavklart -> UførevilkårStatus.HarUføresakTilBehandling
}

internal data class VurderingsperiodeUføreJson(
    val id: String,
    val opprettet: String,
    val resultat: UførevilkårStatus,
    val grunnlag: UføregrunnlagJson?,
    val periode: PeriodeJson,
    val begrunnelse: String?,
)
