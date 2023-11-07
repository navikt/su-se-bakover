package no.nav.su.se.bakover.domain.revurdering.beregning

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingKanBeregnes

internal interface BeregnRevurderingStrategy {
    fun beregn(): Either<KunneIkkeBeregneRevurdering, Pair<Revurdering, Beregning>>
}

internal class Normal(
    private val revurdering: Revurdering,
    private val beregningStrategyFactory: BeregningStrategyFactory,
) : BeregnRevurderingStrategy {
    override fun beregn(): Either<KunneIkkeBeregneRevurdering, Pair<Revurdering, Beregning>> {
        require(!revurdering.grunnlagsdataOgVilkårsvurderinger.harAvkortingsfradrag()) {
            "Vi støtter ikke lenger å beregne med avkortingsfradrag. For sakId ${revurdering.saksnummer}"
        }
        if (revurdering !is RevurderingKanBeregnes) {
            throw IllegalStateException("Kan ikke beregne en revurdering i feil tilstand. Må være en av opprettet, beregnet, simulert eller underkjent; men var ${revurdering::class}")
        }

        return Pair(revurdering, gjørBeregning(revurdering, beregningStrategyFactory)).right()
    }
}

private fun gjørBeregning(
    revurdering: Revurdering,
    beregningStrategyFactory: BeregningStrategyFactory,
): Beregning {
    return beregningStrategyFactory.beregn(
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
            grunnlagsdata = revurdering.grunnlagsdata,
            vilkårsvurderinger = revurdering.vilkårsvurderinger,
        ),
        // kan ikke legge til begrunnelse for inntekt/fradrag
        begrunnelse = null,
        sakstype = revurdering.sakstype,
    )
}
