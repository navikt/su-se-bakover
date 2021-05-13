package no.nav.su.se.bakover.domain.grunnlag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
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
        val fradrag: Fradrag
    ) : Grunnlag()
}
