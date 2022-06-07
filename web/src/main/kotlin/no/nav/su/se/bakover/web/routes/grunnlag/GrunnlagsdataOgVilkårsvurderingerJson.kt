package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.OpplysningspliktVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.toJson

internal data class GrunnlagsdataOgVilkårsvurderingerJson(
    val uføre: UføreVilkårJson?,
    val fradrag: List<FradragJson>,
    val bosituasjon: List<BosituasjonJson>,
    val formue: FormuevilkårJson?,
    val utenlandsopphold: UtenlandsoppholdVilkårJson?,
    val opplysningsplikt: OpplysningspliktVilkårJson?,
) {
    companion object {
        fun create(
            grunnlagsdata: Grunnlagsdata,
            vilkårsvurderinger: Vilkårsvurderinger,
            satsFactory: SatsFactory,
        ): GrunnlagsdataOgVilkårsvurderingerJson {
            return GrunnlagsdataOgVilkårsvurderingerJson(
                uføre = vilkårsvurderinger.uføreVilkår().fold(
                    {
                        TODO("vilkårsvurdering_alder json for aldersvilkår ikke implementert enda")
                    },
                    {
                        it.toJson()
                    }
                ),
                fradrag = grunnlagsdata.fradragsgrunnlag.map { it.fradrag.toJson() },
                bosituasjon = grunnlagsdata.bosituasjon.toJson(),
                formue = vilkårsvurderinger.formueVilkår().toJson(satsFactory),
                utenlandsopphold = vilkårsvurderinger.utenlandsoppholdVilkår().toJson(),
                opplysningsplikt = vilkårsvurderinger.opplysningspliktVilkår().toJson(),
            )
        }
    }
}

internal fun GrunnlagsdataOgVilkårsvurderinger.toJson(satsFactory: SatsFactory): GrunnlagsdataOgVilkårsvurderingerJson {
    return GrunnlagsdataOgVilkårsvurderingerJson.create(
        grunnlagsdata = this.grunnlagsdata,
        vilkårsvurderinger = this.vilkårsvurderinger,
        satsFactory = satsFactory,
    )
}
