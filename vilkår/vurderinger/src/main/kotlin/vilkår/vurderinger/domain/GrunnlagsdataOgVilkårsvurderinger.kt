package vilkår.vurderinger.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import arrow.core.separateEither
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.Vilkår
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.opplysningsplikt.domain.KunneIkkeLageOpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktBeskrivelse
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.Opplysningspliktgrunnlag
import vilkår.opplysningsplikt.domain.VurderingsperiodeOpplysningsplikt
import java.util.UUID

interface GrunnlagsdataOgVilkårsvurderinger {
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger
    val eksterneGrunnlag: EksterneGrunnlag

    val eps: List<Fnr> get() = grunnlagsdata.eps

    fun periode(): Periode? {
        return grunnlagsdata.periode ?: vilkårsvurderinger.periode
    }

    fun erVurdert(): Boolean = vilkårsvurderinger.erVurdert && grunnlagsdata.erUtfylt
    fun harVurdertOpplysningsplikt(): Boolean = vilkårsvurderinger.opplysningsplikt is OpplysningspliktVilkår.Vurdert

    fun oppdaterVilkår(vilkår: Vilkår): GrunnlagsdataOgVilkårsvurderinger

    fun oppdaterGrunnlagsdata(grunnlagsdata: Grunnlagsdata): GrunnlagsdataOgVilkårsvurderinger
    fun oppdaterVilkårsvurderinger(vilkårsvurderinger: Vilkårsvurderinger): GrunnlagsdataOgVilkårsvurderinger

    /**
     * Erstatter eksisterende fradragsgrunnlag med nye.
     */
    fun oppdaterFradragsgrunnlag(fradragsgrunnlag: List<Fradragsgrunnlag>): GrunnlagsdataOgVilkårsvurderinger

    /* jah: Beholdes så lenge command-typene støtter fradragstypen avkorting. En mulighet er å splitte fradragstypene i command/query slik at vi ikke trenger bekymre oss for at ugyldige fradrag sniker seg inn i beregningen. */
    fun harAvkortingsfradrag(): Boolean
    fun oppdaterEksterneGrunnlag(eksternGrunnlag: EksterneGrunnlag): GrunnlagsdataOgVilkårsvurderinger
    fun oppdaterOpplysningsplikt(opplysningspliktVilkår: OpplysningspliktVilkår): GrunnlagsdataOgVilkårsvurderinger

    fun sjekkOmGrunnlagOgVilkårErKonsistent(): Either<Set<Konsistensproblem>, Unit> {
        return setOf(
            Uføre(this.vilkårsvurderinger.uføreVilkårKastHvisAlder().grunnlag).resultat,
            BosituasjonKonsistensProblem(this.grunnlagsdata.bosituasjonSomFullstendig()).resultat,
            Formue(this.vilkårsvurderinger.formue.grunnlag).resultat,
            BosituasjonOgFradrag(
                this.grunnlagsdata.bosituasjonSomFullstendig(),
                this.grunnlagsdata.fradragsgrunnlag,
            ).resultat,
            BosituasjonOgFormue(
                this.grunnlagsdata.bosituasjonSomFullstendig(),
                this.vilkårsvurderinger.formue.grunnlag,
            ).resultat,
        ).let {
            val problemer = it.separateEither().first.flatten().toSet()
            if (problemer.isEmpty()) Unit.right() else problemer.left()
        }
    }

    fun copyWithNewIds(): GrunnlagsdataOgVilkårsvurderinger
}

/**
 * Skal kun kastes fra undertyper av [GrunnlagsdataOgVilkårsvurderinger]
 */
fun GrunnlagsdataOgVilkårsvurderinger.kastHvisPerioderIkkeErLike() {
    if (grunnlagsdata.periode != null && vilkårsvurderinger.periode != null) {
        require(grunnlagsdata.periode == vilkårsvurderinger.periode) {
            "Grunnlagsdataperioden (${grunnlagsdata.periode}) må være lik vilkårsvurderingerperioden (${vilkårsvurderinger.periode})"
        }
        // TODO jah: Dersom periodene ikke er sammenhengende bør vi heller sjekke at periodene er like.
    }
    // TODO jah: Grunnlagsdata og Vilkårsvurderinger bør ha tilsvarende sjekk og vurderingsperiodene bør sjekke at de er sortert og uten duplikater
}

inline fun <reified T : GrunnlagsdataOgVilkårsvurderinger> GrunnlagsdataOgVilkårsvurderinger.avslåPgaOpplysningsplikt(
    tidspunkt: Tidspunkt,
    periode: Periode,
): Either<KunneIkkeLageOpplysningspliktVilkår, T> {
    return OpplysningspliktVilkår.Vurdert.tryCreate(
        vurderingsperioder = nonEmptyListOf(
            VurderingsperiodeOpplysningsplikt.create(
                id = UUID.randomUUID(),
                opprettet = tidspunkt,
                periode = periode,
                grunnlag = Opplysningspliktgrunnlag(
                    id = UUID.randomUUID(),
                    opprettet = tidspunkt,
                    periode = periode,
                    beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                ),
            ),
        ),
    ).map { oppdaterOpplysningsplikt(it) as T }
}

fun GrunnlagsdataOgVilkårsvurderinger.krevAlleVilkårInnvilget() {
    vilkårsvurderinger.erInnvilget().also {
        check(it) { "Ugyldig tilstand, alle vilkår må være innvilget, var: $it" }
    }
}

fun GrunnlagsdataOgVilkårsvurderinger.krevMinstEttAvslag() {
    vilkårsvurderinger.erAvslag().also {
        check(it) { "Ugyldig tilstand, minst et vilkår må være avslått, var: $it" }
    }
}
