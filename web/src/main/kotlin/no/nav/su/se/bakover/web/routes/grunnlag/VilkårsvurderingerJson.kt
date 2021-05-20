package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson

internal data class VilkårsvurderingerJson(
    val uføre: UføreVilkårJson?,
    val fradrag: List<FradragJson>
) {
    companion object {
        fun create(g: Grunnlagsdata, v: Vilkårsvurderinger) =
            VilkårsvurderingerJson(
                uføre = when (val uføre = v.uføre) {
                    Vilkår.IkkeVurdert.Uførhet -> null
                    is Vilkår.Vurdert.Uførhet -> uføre.toJson()
                },
                fradrag = g.fradragsgrunnlag.map { it.fradrag.toJson() }
            )
    }
}

internal data class VurderingsperiodeUføreJson(
    val id: String,
    val opprettet: String,
    val resultat: Behandlingsinformasjon.Uførhet.Status,
    val grunnlag: UføregrunnlagJson?,
    val periode: PeriodeJson,
    val begrunnelse: String?,
)
