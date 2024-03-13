package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening
import no.nav.su.se.bakover.domain.vilkår.pensjonsVilkår
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragResponseJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragResponseJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.vilkår.FamiliegjenforeningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.FamiliegjenforeningVilkårJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.vilkår.PersonligOppmøteVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.FastOppholdINorgeVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.toJson
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.FlyktningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.toJson
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.InstitusjonsoppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.InstitusjonsoppholdJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.LovligOppholdVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.lovligopphold.LovligOppholdVilkårJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.OpplysningspliktVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.opplysningsplikt.toJson
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.PensjonsVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.toJson
import no.nav.su.se.bakover.web.routes.vilkår.toJson
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger
import vilkår.vurderinger.domain.Vilkårsvurderinger

internal data class GrunnlagsdataOgVilkårsvurderingerJson(
    val uføre: UføreVilkårJson?,
    val lovligOpphold: LovligOppholdVilkårJson?,
    val fradrag: List<FradragResponseJson>,
    val bosituasjon: List<BosituasjonJson>,
    val formue: FormuevilkårJson?,
    val utenlandsopphold: UtenlandsoppholdVilkårJson?,
    val opplysningsplikt: OpplysningspliktVilkårJson?,
    val pensjon: PensjonsVilkårJson?,
    val familiegjenforening: FamiliegjenforeningVilkårJson?,
    val flyktning: FlyktningVilkårJson?,
    val fastOpphold: FastOppholdINorgeVilkårJson?,
    val personligOppmøte: PersonligOppmøteVilkårJson?,
    val institusjonsopphold: InstitusjonsoppholdJson?,
) {
    companion object {
        fun create(
            grunnlagsdata: Grunnlagsdata,
            vilkårsvurderinger: Vilkårsvurderinger,
            formuegrenserFactory: FormuegrenserFactory,
        ): GrunnlagsdataOgVilkårsvurderingerJson {
            return GrunnlagsdataOgVilkårsvurderingerJson(
                uføre = vilkårsvurderinger.uføreVilkår().fold(
                    { null },
                    { it.toJson() },
                ),
                lovligOpphold = vilkårsvurderinger.lovligOppholdVilkår().toJson(),
                fradrag = grunnlagsdata.fradragsgrunnlag.map { it.fradrag.toJson() },
                bosituasjon = grunnlagsdata.bosituasjon.toJson(),
                formue = vilkårsvurderinger.formueVilkår().toJson(formuegrenserFactory),
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
                flyktning = vilkårsvurderinger.flyktningVilkår().fold(
                    { null },
                    { it.toJson() },
                ),
                fastOpphold = vilkårsvurderinger.fastOppholdVilkår().toJson(),
                personligOppmøte = vilkårsvurderinger.personligOppmøteVilkår().toJson(),
                institusjonsopphold = vilkårsvurderinger.institusjonsopphold.toJson(),
            )
        }
    }
}

internal fun GrunnlagsdataOgVilkårsvurderinger.toJson(formuegrenserFactory: FormuegrenserFactory): GrunnlagsdataOgVilkårsvurderingerJson {
    return GrunnlagsdataOgVilkårsvurderingerJson.create(
        grunnlagsdata = this.grunnlagsdata,
        vilkårsvurderinger = this.vilkårsvurderinger,
        formuegrenserFactory = formuegrenserFactory,
    )
}

internal fun GrunnlagsdataOgVilkårsvurderinger.toStringifiedJson(formuegrenserFactory: FormuegrenserFactory): String =
    GrunnlagsdataOgVilkårsvurderingerJson.create(
        grunnlagsdata = this.grunnlagsdata,
        vilkårsvurderinger = this.vilkårsvurderinger,
        formuegrenserFactory = formuegrenserFactory,
    ).let { serialize(it) }
