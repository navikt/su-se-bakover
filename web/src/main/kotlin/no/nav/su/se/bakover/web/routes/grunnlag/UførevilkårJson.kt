package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Inngangsvilkår
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import java.time.format.DateTimeFormatter

internal data class UføreVilkårJson(
    val vilkår: String,
    val vurdering: VurderingsperiodeUføreJson,
    val resultat: Behandlingsinformasjon.Uførhet.Status,
)

internal fun Vurderingsperiode<Grunnlag.Uføregrunnlag>.toJson() = VurderingsperiodeUføreJson(
    id = id.toString(),
    opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
    resultat = resultat.toStatusString(),
    grunnlag = grunnlag?.toJson(),
    periode = periode.toJson(),
    begrunnelse = begrunnelse,
)

internal fun Vilkår.Vurdert.Uførhet.toJson() = UføreVilkårJson(
    vilkår = vilkår.stringValue(),
    vurdering = vurdering.first().toJson(),
    resultat = resultat.toStatusString(),
)

internal fun Vilkår.IkkeVurdert.Uførhet.toJson() = null

internal fun Inngangsvilkår.stringValue() = when (this) {
    Inngangsvilkår.BorOgOppholderSegINorge -> TODO()
    Inngangsvilkår.Flyktning -> TODO()
    Inngangsvilkår.Formue -> TODO()
    Inngangsvilkår.Oppholdstillatelse -> TODO()
    Inngangsvilkår.PersonligOppmøte -> TODO()
    Inngangsvilkår.Uførhet -> "Uførhet"
    Inngangsvilkår.innlagtPåInstitusjon -> TODO()
    Inngangsvilkår.utenlandsoppholdOver90Dager -> TODO()
}

internal fun Resultat.toStatusString() = when (this) {
    Resultat.Avslag -> Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt
    Resultat.Innvilget -> Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt
    Resultat.Uavklart -> Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling
}
