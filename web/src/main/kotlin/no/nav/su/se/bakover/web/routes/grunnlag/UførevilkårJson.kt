package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.vilkår.Inngangsvilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import java.time.format.DateTimeFormatter

internal data class UføreVilkårJson(
    val vilkår: String,
    val vurderinger: List<VurderingsperiodeUføreJson>,
    val resultat: Behandlingsinformasjon.Uførhet.Status,
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
    vilkår = vilkår.toJson(),
    vurderinger = vurderingsperioder.map { it.toJson() },
    resultat = resultat.toUførhetStatusString(),
)

internal fun Inngangsvilkår.toJson() = when (this) {
    Inngangsvilkår.Uførhet -> "Uførhet"
    Inngangsvilkår.Formue -> "Formue"
}

internal fun Resultat.toUførhetStatusString() = when (this) {
    Resultat.Avslag -> Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt
    Resultat.Innvilget -> Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt
    Resultat.Uavklart -> Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling
}

internal data class VurderingsperiodeUføreJson(
    val id: String,
    val opprettet: String,
    val resultat: Behandlingsinformasjon.Uførhet.Status,
    val grunnlag: UføregrunnlagJson?,
    val periode: PeriodeJson,
    val begrunnelse: String?,
)
