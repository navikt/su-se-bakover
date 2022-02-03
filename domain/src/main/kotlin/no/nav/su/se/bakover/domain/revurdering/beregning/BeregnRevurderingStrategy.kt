package no.nav.su.se.bakover.domain.revurdering

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
import java.time.Clock
import kotlin.math.roundToInt

internal interface BeregnRevurderingStrategy {
    fun beregn(): Either<Revurdering.KunneIkkeBeregneRevurdering, Pair<OpprettetRevurdering, Beregning>>
}

internal class Normal(
    private val revurdering: Revurdering,
    private val clock: Clock,
) : BeregnRevurderingStrategy {
    override fun beregn(): Either<Revurdering.KunneIkkeBeregneRevurdering, Pair<OpprettetRevurdering, Beregning>> {
        return beregnUtenAvkorting(revurdering, clock).right()
    }
}

internal class VidereførAvkorting(
    private val revurdering: Revurdering,
    private val avkortingsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
    private val clock: Clock,
) : BeregnRevurderingStrategy {
    override fun beregn(): Either<Revurdering.KunneIkkeBeregneRevurdering, Pair<OpprettetRevurdering, Beregning>> {
        val (utenAvkorting, beregningUtenAvkorting) = beregnUtenAvkorting(revurdering, clock)

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
            it to gjørBeregning(it, clock)
        }.right()
    }
}

internal class AnnullerAvkorting(
    private val revurdering: Revurdering,
    private val clock: Clock,
) : BeregnRevurderingStrategy {
    override fun beregn(): Either<Revurdering.KunneIkkeBeregneRevurdering, Pair<OpprettetRevurdering, Beregning>> {
        return beregnUtenAvkorting(revurdering, clock).right()
    }
}

private fun beregnUtenAvkorting(revurdering: Revurdering, clock: Clock): Pair<OpprettetRevurdering, Beregning> {
    return revurdering.oppdaterFradrag(
        fradragsgrunnlag = revurdering.grunnlagsdata.fradragsgrunnlag.filterNot { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold },
    ).getOrHandle {
        throw IllegalStateException(Revurdering.KunneIkkeLeggeTilFradrag.UgyldigTilstand(revurdering::class).toString())
    }.let {
        it to gjørBeregning(it, clock)
    }
}

private fun gjørBeregning(
    revurdering: OpprettetRevurdering,
    clock: Clock,
): Beregning {
    return BeregningStrategyFactory(clock).beregn(
        GrunnlagsdataOgVilkårsvurderinger(
            grunnlagsdata = revurdering.grunnlagsdata,
            vilkårsvurderinger = revurdering.vilkårsvurderinger,
        ),
        beregningsPeriode = revurdering.periode,
        // kan ikke legge til begrunnelse for inntekt/fradrag
        null,
    )
}
