package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson

internal data class VilkårsvurderingerJson(
    val uføre: UføreVilkårJson?,
)

internal fun Vilkårsvurderinger.toJson() = VilkårsvurderingerJson(
    uføre = when (uføre) {
        Vilkår.IkkeVurdert.Uførhet -> (uføre as Vilkår.IkkeVurdert.Uførhet).toJson()
        is Vilkår.Vurdert.Uførhet -> (uføre as Vilkår.Vurdert.Uførhet).toJson()
    },
)

internal data class VurderingsperiodeUføreJson(
    val id: String,
    val opprettet: String,
    val resultat: Behandlingsinformasjon.Uførhet.Status,
    val grunnlag: UføregrunnlagJson?,
    val periode: PeriodeJson,
    val begrunnelse: String,
)
