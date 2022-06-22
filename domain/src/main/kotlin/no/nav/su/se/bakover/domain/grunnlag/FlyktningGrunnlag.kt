package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

data class FlyktningGrunnlag(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val periode: Periode,
) : Grunnlag(), KanPlasseresPåTidslinje<FlyktningGrunnlag> {

    fun oppdaterPeriode(periode: Periode): FlyktningGrunnlag {
        return tryCreate(
            id = id,
            opprettet = opprettet,
            periode = periode,
        ).getOrHandle { throw IllegalArgumentException(it.toString()) }
    }

    override fun copy(args: CopyArgs.Tidslinje): FlyktningGrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(id = UUID.randomUUID())
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(id = UUID.randomUUID(), periode = args.periode)
        }
        is CopyArgs.Tidslinje.Maskert -> {
            copy(args.args).copy(opprettet = opprettet.plusUnits(1))
        }
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is FlyktningGrunnlag
    }

    companion object {
        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
        ): Either<KunneIkkeLageFlyktningGrunnlag, FlyktningGrunnlag> {
            return FlyktningGrunnlag(
                id = id,
                opprettet = opprettet,
                periode = periode,
            ).right()
        }
    }
}

sealed class KunneIkkeLageFlyktningGrunnlag
