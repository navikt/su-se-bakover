package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import java.time.format.DateTimeFormatter

internal data class FormuevilkårJson(
    val vilkår: String,
    val vurderinger: List<VurderingsperiodeFormueJson>,
    val resultat: Behandlingsinformasjon.Uførhet.Status,
)

internal data class VurderingsperiodeFormueJson(
    val id: String,
    val opprettet: String,
    val resultat: Behandlingsinformasjon.Uførhet.Status,
    val grunnlag: FormuegrunnlagJson?,
    val periode: PeriodeJson,
    val begrunnelse: String?,
)

internal fun Vilkår.Formue.toJson(): FormuevilkårJson? = when (this) {
    Vilkår.Formue.IkkeVurdert -> null
    is Vilkår.Formue.Vurdert -> FormuevilkårJson(
        vilkår = vilkår.toJson(),
        vurderinger = vurderingsperioder.map { it.toJson() },
        resultat = resultat.toStatusString(),
    )
}

internal fun Vurderingsperiode.Formue.toJson(): VurderingsperiodeFormueJson {
    return VurderingsperiodeFormueJson(
        id = id.toString(),
        opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
        resultat = resultat.toStatusString(),
        grunnlag = grunnlag?.toJson(),
        periode = periode.toJson(),
        begrunnelse = begrunnelse,
    )
}
