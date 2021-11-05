package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

data class InstitusjonsoppholdGrunnlag(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val periode: Periode,
) : Grunnlag(), KanPlasseresPåTidslinje<InstitusjonsoppholdGrunnlag> {

    fun oppdaterPeriode(periode: Periode): InstitusjonsoppholdGrunnlag {
        return tryCreate(
            id = id,
            opprettet = opprettet,
            periode = periode,
        ).getOrHandle { throw IllegalArgumentException(it.toString()) }
    }

    override fun copy(args: CopyArgs.Tidslinje): InstitusjonsoppholdGrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(id = UUID.randomUUID())
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(id = UUID.randomUUID(), periode = args.periode)
        }
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is InstitusjonsoppholdGrunnlag
    }

    companion object {
        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
        ): Either<KunneIkkeLageInstitusjonsoppholdgrunnlag, InstitusjonsoppholdGrunnlag> {
            return InstitusjonsoppholdGrunnlag(
                id = id,
                opprettet = opprettet,
                periode = periode,
            ).right()
        }
    }
}

sealed class KunneIkkeLageInstitusjonsoppholdgrunnlag
