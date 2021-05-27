package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.extensions.either.applicative.applicative
import arrow.core.extensions.list.traverse.traverse
import arrow.core.fix
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

sealed class Grunnlag {
    abstract val id: UUID

    /**
     * @throws IllegalArgumentException hvis forventetInntekt er negativ
     */
    data class Uføregrunnlag(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val periode: Periode,
        val uføregrad: Uføregrad,
        /** Kan ikke være negativ. */
        val forventetInntekt: Int,
    ) : Grunnlag(), KanPlasseresPåTidslinje<Uføregrunnlag> {
        init {
            if (forventetInntekt < 0) throw IllegalArgumentException("forventetInntekt kan ikke være mindre enn 0")
        }

        fun oppdaterPeriode(periode: Periode): Uføregrunnlag {
            return this.copy(periode = periode)
        }

        override fun copy(args: CopyArgs.Tidslinje): Uføregrunnlag = when (args) {
            CopyArgs.Tidslinje.Full -> {
                this.copy(id = UUID.randomUUID())
            }
            is CopyArgs.Tidslinje.NyPeriode -> {
                this.copy(id = UUID.randomUUID(), periode = args.periode)
            }
        }
    }

    data class Flyktninggrunnlag(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val periode: Periode,
    ) : Grunnlag(), KanPlasseresPåTidslinje<Flyktninggrunnlag> {

        fun oppdaterPeriode(periode: Periode): Flyktninggrunnlag {
            return this.copy(periode = periode)
        }

        override fun copy(args: CopyArgs.Tidslinje): Flyktninggrunnlag = when (args) {
            CopyArgs.Tidslinje.Full -> {
                this.copy(id = UUID.randomUUID())
            }
            is CopyArgs.Tidslinje.NyPeriode -> {
                this.copy(id = UUID.randomUUID(), periode = args.periode)
            }
        }
    }

    data class Fradragsgrunnlag(
        override val id: UUID = UUID.randomUUID(),
        val opprettet: Tidspunkt = Tidspunkt.now(),
        val fradrag: Fradrag,
    ) : Grunnlag() {

        companion object Validator {
            fun List<Fradragsgrunnlag>.valider(behandlingsperiode: Periode): Either<UgyldigFradragsgrunnlag, Unit> {
                return map {
                    it.valider(behandlingsperiode)
                }.traverse(Either.applicative(), ::identity).fix().map {
                    it.fix()
                }
            }

            fun Fradragsgrunnlag.valider(behandlingsperiode: Periode): Either<UgyldigFradragsgrunnlag, Unit> {
                if (!(behandlingsperiode inneholder fradrag.periode))
                    return UgyldigFradragsgrunnlag.UtenforBehandlingsperiode.left()
                if (setOf(Fradragstype.ForventetInntekt, Fradragstype.BeregnetFradragEPS, Fradragstype.UnderMinstenivå).contains(fradrag.fradragstype))
                    return UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()
                return Unit.right()
            }

            sealed class UgyldigFradragsgrunnlag {
                object UtenforBehandlingsperiode : UgyldigFradragsgrunnlag()
                object UgyldigFradragstypeForGrunnlag : UgyldigFradragsgrunnlag()
            }
        }
    }
}
