package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.separateEither
import vilkår.vurderinger.domain.BosituasjonKonsistensProblem
import vilkår.vurderinger.domain.BosituasjonOgFormue
import vilkår.vurderinger.domain.BosituasjonOgFradrag
import vilkår.vurderinger.domain.Formue
import vilkår.vurderinger.domain.Konsistensproblem
import vilkår.vurderinger.domain.Uføre

fun GrunnlagsdataOgVilkårsvurderinger.sjekkOmGrunnlagOgVilkårErKonsistent(): Either<Set<Konsistensproblem>, Unit> {
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
