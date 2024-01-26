package no.nav.su.se.bakover.utenlandsopphold.domain.vilkår

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

data class Utenlandsoppholdgrunnlag(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val periode: Periode,
) : Grunnlag, KanPlasseresPåTidslinje<Utenlandsoppholdgrunnlag> {

    fun oppdaterPeriode(periode: Periode): Utenlandsoppholdgrunnlag {
        return tryCreate(
            id = id,
            opprettet = opprettet,
            periode = periode,
        ).getOrElse { throw IllegalArgumentException(it.toString()) }
    }

    override fun copy(args: CopyArgs.Tidslinje): Utenlandsoppholdgrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(id = UUID.randomUUID())
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(id = UUID.randomUUID(), periode = args.periode)
        }

        else -> TODO("fjern meg senere")
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is Utenlandsoppholdgrunnlag
    }

    companion object {
        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
        ): Either<KunneIkkeLageUtenlandsoppholdgrunnlag, Utenlandsoppholdgrunnlag> {
            return Utenlandsoppholdgrunnlag(
                id = id,
                opprettet = opprettet,
                periode = periode,
            ).right()
        }
    }
}

sealed interface KunneIkkeLageUtenlandsoppholdgrunnlag
