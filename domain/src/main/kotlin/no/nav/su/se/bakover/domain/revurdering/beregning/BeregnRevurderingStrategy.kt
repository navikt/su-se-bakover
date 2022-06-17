package no.nav.su.se.bakover.domain.revurdering.beregning

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.avkorting.Avkortingsplan
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import java.time.Clock
import kotlin.math.roundToInt

internal interface BeregnRevurderingStrategy {
    fun beregn(): Either<Revurdering.KunneIkkeBeregneRevurdering, Pair<OpprettetRevurdering, Beregning>>
}

internal class Normal(
    private val revurdering: Revurdering,
    private val beregningStrategyFactory: BeregningStrategyFactory,
) : BeregnRevurderingStrategy {
    override fun beregn(): Either<Revurdering.KunneIkkeBeregneRevurdering, Pair<OpprettetRevurdering, Beregning>> {
        return beregnUtenAvkorting(revurdering, beregningStrategyFactory).right()
    }
}

internal class VidereførAvkorting(
    private val revurdering: Revurdering,
    private val avkortingsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
    private val clock: Clock,
    private val beregningStrategyFactory: BeregningStrategyFactory,
) : BeregnRevurderingStrategy {
    override fun beregn(): Either<Revurdering.KunneIkkeBeregneRevurdering, Pair<OpprettetRevurdering, Beregning>> {
        val (utenAvkorting, beregningUtenAvkorting) = beregnUtenAvkorting(revurdering, beregningStrategyFactory)

        val fradragForAvkorting = Avkortingsplan(
            feilutbetaltBeløp = avkortingsgrunnlag
                .sumOf { it.periode.getAntallMåneder() * it.månedsbeløp }
                .roundToInt(),
            beregning = beregningUtenAvkorting,
            clock = clock,
        ).lagFradrag().getOrHandle {
            return Revurdering.KunneIkkeBeregneRevurdering.AvkortingErUfullstendig.left()
        }

        return utenAvkorting.oppdaterFradrag(
            fradragsgrunnlag = utenAvkorting.grunnlagsdata.fradragsgrunnlag + fradragForAvkorting,
        ).getOrHandle {
            throw IllegalStateException(
                Revurdering.KunneIkkeLeggeTilFradrag.UgyldigTilstand(utenAvkorting::class).toString(),
            )
        }.let {
            it to gjørBeregning(it, beregningStrategyFactory)
        }.right()
    }
}

internal class AnnullerAvkorting(
    private val revurdering: Revurdering,
    private val beregningStrategyFactory: BeregningStrategyFactory,
) : BeregnRevurderingStrategy {
    override fun beregn(): Either<Revurdering.KunneIkkeBeregneRevurdering, Pair<OpprettetRevurdering, Beregning>> {
        return beregnUtenAvkorting(revurdering, beregningStrategyFactory).right()
    }
}

private fun beregnUtenAvkorting(
    revurdering: Revurdering,
    beregningStrategyFactory: BeregningStrategyFactory,
): Pair<OpprettetRevurdering, Beregning> {
    return revurdering.oppdaterFradrag(
        fradragsgrunnlag = revurdering.grunnlagsdata.fradragsgrunnlag.filterNot { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold },
    ).getOrHandle {
        throw IllegalStateException(Revurdering.KunneIkkeLeggeTilFradrag.UgyldigTilstand(revurdering::class).toString())
    }.let {
        it to gjørBeregning(it, beregningStrategyFactory)
    }
}

private fun gjørBeregning(
    revurdering: OpprettetRevurdering,
    beregningStrategyFactory: BeregningStrategyFactory,
): Beregning {
    /*   return BeregningStrategyFactory(clock).beregn(revurdering)*/
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
