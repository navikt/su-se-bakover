package no.nav.su.se.bakover.domain.oppdrag

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.time.LocalDate

sealed class Utbetalingslinje : KanPlasseresPåTidslinje<Utbetalingslinje> {
    abstract val id: UUID30 // delytelseId
    abstract override val opprettet: Tidspunkt
    abstract val fraOgMed: LocalDate
    abstract val tilOgMed: LocalDate
    abstract var forrigeUtbetalingslinjeId: UUID30?
    abstract val beløp: Int

    @JsonIgnore
    override fun getPeriode() = Periode.create(fraOgMed, tilOgMed)

    data class Ny(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val fraOgMed: LocalDate,
        override val tilOgMed: LocalDate,
        override var forrigeUtbetalingslinjeId: UUID30?,
        override val beløp: Int,
    ) : Utbetalingslinje() {
        init {
            require(fraOgMed < tilOgMed) { "fraOgMed må være tidligere enn tilOgMed" }
        }

        fun link(other: Utbetalingslinje) {
            forrigeUtbetalingslinjeId = other.id
        }

        override fun copy(args: CopyArgs.Tidslinje) = when (args) {
            is CopyArgs.Tidslinje.Full -> this.copy()
            is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                fraOgMed = args.periode.getFraOgMed(),
                tilOgMed = args.periode.getTilOgMed(),
            )
        }
    }

    data class Endring constructor(
        override val id: UUID30,
        override val opprettet: Tidspunkt,
        override val fraOgMed: LocalDate,
        override val tilOgMed: LocalDate,
        override var forrigeUtbetalingslinjeId: UUID30?,
        override val beløp: Int,
        val statusendring: Statusendring,
    ) : Utbetalingslinje() {

        constructor(
            utbetalingslinje: Utbetalingslinje,
            statusendring: Statusendring,
        ) : this(
            id = utbetalingslinje.id,
            opprettet = Tidspunkt.now(),
            fraOgMed = utbetalingslinje.fraOgMed,
            tilOgMed = utbetalingslinje.tilOgMed,
            forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
            beløp = utbetalingslinje.beløp,
            statusendring = statusendring,
        )

        override fun copy(args: CopyArgs.Tidslinje) = when (args) {
            is CopyArgs.Tidslinje.Full -> this.copy()
            is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                fraOgMed = args.periode.getFraOgMed(),
                tilOgMed = args.periode.getTilOgMed(),
            )
        }
    }

    data class Statusendring(
        val status: LinjeStatus,
        val fraOgMed: LocalDate,
    )

    enum class LinjeStatus {
        OPPHØR;
    }
}
