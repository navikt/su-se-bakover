package vilkår.fastopphold.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.domain.Vurdering
import vilkår.domain.Vurderingsperiode
import java.util.UUID

data class VurderingsperiodeFastOppholdINorge private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val grunnlag: FastOppholdINorgeGrunnlag?,
    override val vurdering: Vurdering,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodeFastOppholdINorge> {

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeFastOppholdINorge {
        return create(
            id = id,
            opprettet = opprettet,
            vurdering = vurdering,
            periode = stønadsperiode.periode,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeFastOppholdINorge = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(
                id = UUID.randomUUID(),
                grunnlag = grunnlag?.copy(args),
            )
        }

        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(
                id = UUID.randomUUID(),
                periode = args.periode,
                grunnlag = grunnlag?.copy(args),
            )
        }

        else -> TODO("fjern meg senere")
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodeFastOppholdINorge &&
            vurdering == other.vurdering &&
            when {
                grunnlag != null && other.grunnlag != null -> grunnlag.erLik(other.grunnlag)
                grunnlag == null && other.grunnlag == null -> true
                else -> false
            }
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            periode: Periode,
        ): VurderingsperiodeFastOppholdINorge {
            return tryCreate(id, opprettet, vurdering, periode).getOrElse {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            vurderingsperiode: Periode,
        ): Either<UgyldigVurderingsperiode, VurderingsperiodeFastOppholdINorge> {
            return VurderingsperiodeFastOppholdINorge(
                id = id,
                opprettet = opprettet,
                vurdering = vurdering,
                periode = vurderingsperiode,
                grunnlag = null,
            ).right()
        }
    }

    sealed interface UgyldigVurderingsperiode {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode
    }
}
