package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson.Companion.toJson

internal data class GrunnlagsdataOgVilkårsvurderingerJson(
    val uføre: UføreVilkårJson?,
    val fradrag: List<FradragJson>,
    val bosituasjon: List<BosituasjonJson>,
    val formue: FormuevilkårJson?,
    val oppholdIUtlandet: OppholdIUtlandetVilkårJson?,
) {
    companion object {
        fun create(
            grunnlagsdata: Grunnlagsdata,
            vilkårsvurderinger: Vilkårsvurderinger,
        ): GrunnlagsdataOgVilkårsvurderingerJson {
            return GrunnlagsdataOgVilkårsvurderingerJson(
                uføre = vilkårsvurderinger.uføreJson(),
                fradrag = grunnlagsdata.fradragsgrunnlag.map { it.fradrag.toJson() },
                bosituasjon = grunnlagsdata.bosituasjon.toJson(),
                formue = vilkårsvurderinger.formueJson(),
                oppholdIUtlandet = vilkårsvurderinger.oppholdIUtlandJson(),
            )
        }
    }
}

internal fun Vilkårsvurderinger.uføreJson(): UføreVilkårJson? {
    return when (this) {
        is Vilkårsvurderinger.Revurdering -> {
            when (val v = uføre) {
                Vilkår.Uførhet.IkkeVurdert -> null
                is Vilkår.Uførhet.Vurdert -> v.toJson()
            }
        }
        is Vilkårsvurderinger.Søknadsbehandling -> {
            when (val v = uføre) {
                Vilkår.Uførhet.IkkeVurdert -> null
                is Vilkår.Uførhet.Vurdert -> v.toJson()
            }
        }
    }
}

internal fun Vilkårsvurderinger.formueJson(): FormuevilkårJson? {
    return when (this) {
        is Vilkårsvurderinger.Revurdering -> {
            formue.toJson()
        }
        is Vilkårsvurderinger.Søknadsbehandling -> {
            formue.toJson()
        }
    }
}

internal fun Vilkårsvurderinger.oppholdIUtlandJson(): OppholdIUtlandetVilkårJson? {
    return when (this) {
        is Vilkårsvurderinger.Revurdering -> {
            when (val v = oppholdIUtlandet) {
                OppholdIUtlandetVilkår.IkkeVurdert -> null
                is OppholdIUtlandetVilkår.Vurdert -> v.toJson()
            }
        }
        is Vilkårsvurderinger.Søknadsbehandling -> {
            when (val v = oppholdIUtlandet) {
                OppholdIUtlandetVilkår.IkkeVurdert -> null
                is OppholdIUtlandetVilkår.Vurdert -> v.toJson()
            }
        }
    }
}
