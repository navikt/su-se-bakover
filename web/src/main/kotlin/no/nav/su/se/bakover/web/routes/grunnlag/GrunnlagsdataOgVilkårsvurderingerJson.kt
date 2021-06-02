package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson.Companion.toJson

internal data class GrunnlagsdataOgVilkårsvurderingerJson(
    val uføre: UføreVilkårJson?,
    val fradrag: List<FradragJson>,
    val bosituasjon: List<BosituasjonJson>,
) {
    companion object {
        fun create(grunnlagsdata: Grunnlagsdata, vilkårsvurderinger: Vilkårsvurderinger): GrunnlagsdataOgVilkårsvurderingerJson {
            return GrunnlagsdataOgVilkårsvurderingerJson(
                uføre = when (val uføre = vilkårsvurderinger.uføre) {
                    Vilkår.IkkeVurdert.Uførhet -> null
                    is Vilkår.Vurdert.Uførhet -> uføre.toJson()
                },
                fradrag = grunnlagsdata.fradragsgrunnlag.map { it.fradrag.toJson() },
                bosituasjon = grunnlagsdata.bosituasjon.map {
                    when (it) {
                        is Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen ->
                            BosituasjonJson(
                                type = "DELER_BOLIG_MED_VOKSNE",
                                fnr = null,
                                delerBolig = true,
                                ektemakeEllerSamboerUførFlyktning = null,
                                begrunnelse = it.begrunnelse,
                            )
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning ->
                            BosituasjonJson(
                                type = "EPS_IKKE_UFØR_FLYKTNING",
                                fnr = it.fnr.toString(),
                                delerBolig = null,
                                ektemakeEllerSamboerUførFlyktning = false,
                                begrunnelse = it.begrunnelse,
                            )
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre ->
                            BosituasjonJson(
                                type = "EPS_OVER_67",
                                fnr = it.fnr.toString(),
                                delerBolig = null,
                                ektemakeEllerSamboerUførFlyktning = null,
                                begrunnelse = it.begrunnelse,
                            )
                        is Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning ->
                            BosituasjonJson(
                                type = "EPS_UFØR_FLYKTNING",
                                fnr = it.fnr.toString(),
                                delerBolig = null,
                                ektemakeEllerSamboerUførFlyktning = true,
                                begrunnelse = it.begrunnelse,
                            )
                        is Grunnlag.Bosituasjon.Fullstendig.Enslig ->
                            BosituasjonJson(
                                type = "ENSLIG",
                                fnr = null,
                                delerBolig = false,
                                ektemakeEllerSamboerUførFlyktning = null,
                                begrunnelse = it.begrunnelse,
                            )
                        is Grunnlag.Bosituasjon.Ufullstendig.HarEps ->
                            BosituasjonJson(
                                type = "UFULLSTENDIG_HAR_EPS",
                                fnr = it.fnr.toString(),
                                delerBolig = true,
                                ektemakeEllerSamboerUførFlyktning = null,
                                begrunnelse = null,
                            )
                        is Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps ->
                            BosituasjonJson(
                                type = "UFULLSTENDIG_HAR_IKKE_EPS",
                                fnr = null,
                                delerBolig = null,
                                ektemakeEllerSamboerUførFlyktning = null,
                                begrunnelse = null,
                            )
                    }
                },
            )
        }
    }
}
