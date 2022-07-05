package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.vilkår.FamiliegjenforeningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.FamiliegjenforeningVilkårJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.vilkår.PersonligOppmøteVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.alder.PensjonsVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.alder.toJson
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.LovligOppholdVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.LovligOppholdVilkårJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.OpplysningspliktVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.toJson
import no.nav.su.se.bakover.web.routes.vilkår.toJson

internal data class GrunnlagsdataOgVilkårsvurderingerJson(
    val uføre: UføreVilkårJson?,
    val lovligOpphold: LovligOppholdVilkårJson?,
    val fradrag: List<FradragJson>,
    val bosituasjon: List<BosituasjonJson>,
    val formue: FormuevilkårJson?,
    val utenlandsopphold: UtenlandsoppholdVilkårJson?,
    val opplysningsplikt: OpplysningspliktVilkårJson?,
    val pensjon: PensjonsVilkårJson?,
    val familiegjenforening: FamiliegjenforeningVilkårJson?,
    val personligOppmøte: PersonligOppmøteVilkårJson?,
) {
    companion object {
        fun create(
            grunnlagsdata: Grunnlagsdata,
            vilkårsvurderinger: Vilkårsvurderinger,
            satsFactory: SatsFactory,
        ): GrunnlagsdataOgVilkårsvurderingerJson {
            return GrunnlagsdataOgVilkårsvurderingerJson(
                uføre = vilkårsvurderinger.uføreVilkår().fold(
                    { null },
                    { it.toJson() },
                ),
                lovligOpphold = vilkårsvurderinger.lovligOppholdVilkår().toJson(),
                fradrag = grunnlagsdata.fradragsgrunnlag.map { it.fradrag.toJson() },
                bosituasjon = grunnlagsdata.bosituasjon.toJson(),
                formue = vilkårsvurderinger.formueVilkår().toJson(satsFactory),
                utenlandsopphold = vilkårsvurderinger.utenlandsoppholdVilkår().toJson(),
                opplysningsplikt = vilkårsvurderinger.opplysningspliktVilkår().toJson(),
                pensjon = vilkårsvurderinger.pensjonsVilkår().fold(
                    { null },
                    { it.toJson() },
                ),
                familiegjenforening = vilkårsvurderinger.familiegjenforening().fold(
                    { null },
                    { it.toJson() },
                ),
                personligOppmøte = vilkårsvurderinger.personligOppmøteVilkår().toJson()
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
