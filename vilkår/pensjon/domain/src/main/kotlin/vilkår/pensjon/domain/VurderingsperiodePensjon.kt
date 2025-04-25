package vilkår.pensjon.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.Vurdering
import vilkår.common.domain.Vurderingsperiode
import java.util.UUID

data class VurderingsperiodePensjon private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val grunnlag: Pensjonsgrunnlag,
    override val periode: Periode,
) : Vurderingsperiode,
    KanPlasseresPåTidslinje<VurderingsperiodePensjon> {

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodePensjon {
        return create(
            id = id,
            opprettet = opprettet,
            periode = stønadsperiode.periode,
            vurdering = vurdering,
            grunnlag = grunnlag,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodePensjon = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(
                id = UUID.randomUUID(),
                grunnlag = grunnlag.copy(args),
            )
        }

        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(
                id = UUID.randomUUID(),
                periode = args.periode,
                grunnlag = grunnlag.copy(args),
            )
        }
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodePensjon &&
            vurdering == other.vurdering &&
            grunnlag.erLik(other.grunnlag)
    }

    override fun copyWithNewId(): VurderingsperiodePensjon =
        this.copy(id = UUID.randomUUID(), grunnlag = grunnlag.copyWithNewId())

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
            vurdering: Vurdering,
            grunnlag: Pensjonsgrunnlag,
        ): VurderingsperiodePensjon {
            return tryCreate(id, opprettet, vurdering, periode, grunnlag).getOrElse {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            vurderingsperiode: Periode,
            grunnlag: Pensjonsgrunnlag,
        ): Either<KunneIkkeLagePensjonsVilkår.Vurderingsperiode, VurderingsperiodePensjon> {
            grunnlag.let {
                if (vurderingsperiode != it.periode) return KunneIkkeLagePensjonsVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodePensjon(
                id = id,
                opprettet = opprettet,
                vurdering = vurdering,
                grunnlag = grunnlag,
                periode = vurderingsperiode,
            ).right()
        }
    }
}
