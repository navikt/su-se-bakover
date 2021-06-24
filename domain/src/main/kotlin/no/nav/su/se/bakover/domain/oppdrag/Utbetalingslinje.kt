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

    abstract override val periode: Periode

    data class Ny(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val fraOgMed: LocalDate,
        override val tilOgMed: LocalDate,
        override var forrigeUtbetalingslinjeId: UUID30?,
        override val beløp: Int,
    ) : Utbetalingslinje() {

        @JsonIgnore
        override val periode = Periode.create(fraOgMed, tilOgMed)

        init {
            require(fraOgMed < tilOgMed) { "fraOgMed må være tidligere enn tilOgMed" }
        }

        fun link(other: Utbetalingslinje) {
            forrigeUtbetalingslinjeId = other.id
        }

        override fun copy(args: CopyArgs.Tidslinje) = when (args) {
            is CopyArgs.Tidslinje.Full -> this.copy()
            is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                fraOgMed = args.periode.fraOgMed,
                tilOgMed = args.periode.tilOgMed,
            )
        }
    }

    sealed class Endring : Utbetalingslinje() {
        abstract val linjeStatus: LinjeStatus
        abstract val virkningstidspunkt: LocalDate

        data class Opphør(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override var forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningstidspunkt: LocalDate,
        ) : Endring() {
            override val linjeStatus = LinjeStatus.OPPHØR

            @JsonIgnore
            override val periode = Periode.create(fraOgMed, tilOgMed)

            constructor(
                utbetalingslinje: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
            ) : this(
                id = utbetalingslinje.id,
                opprettet = Tidspunkt.now(),
                fraOgMed = utbetalingslinje.fraOgMed,
                tilOgMed = utbetalingslinje.tilOgMed,
                forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinje.beløp,
                virkningstidspunkt = virkningstidspunkt,
            )

            override fun copy(args: CopyArgs.Tidslinje) = when (args) {
                is CopyArgs.Tidslinje.Full -> this.copy()
                is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                    fraOgMed = args.periode.fraOgMed,
                    tilOgMed = args.periode.tilOgMed,
                )
            }
        }

        data class Stans(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override var forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningstidspunkt: LocalDate,
        ) : Endring() {
            override val linjeStatus = LinjeStatus.STANS

            @JsonIgnore
            override val periode = Periode.create(fraOgMed, tilOgMed)

            constructor(
                utbetalingslinje: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
            ) : this(
                id = utbetalingslinje.id,
                opprettet = Tidspunkt.now(),
                fraOgMed = utbetalingslinje.fraOgMed,
                tilOgMed = utbetalingslinje.tilOgMed,
                forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinje.beløp,
                virkningstidspunkt = virkningstidspunkt,
            )

            override fun copy(args: CopyArgs.Tidslinje) = when (args) {
                is CopyArgs.Tidslinje.Full -> this.copy()
                is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                    fraOgMed = args.periode.fraOgMed,
                    tilOgMed = args.periode.tilOgMed,
                )
            }
        }

        data class Reaktivering(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override var forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningstidspunkt: LocalDate,
        ) : Endring() {
            override val linjeStatus = LinjeStatus.REAKTIVERING

            @JsonIgnore
            override val periode = Periode.create(fraOgMed, tilOgMed)

            constructor(
                utbetalingslinje: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
            ) : this(
                id = utbetalingslinje.id,
                opprettet = Tidspunkt.now(),
                fraOgMed = utbetalingslinje.fraOgMed,
                tilOgMed = utbetalingslinje.tilOgMed,
                forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinje.beløp,
                virkningstidspunkt = virkningstidspunkt,
            )

            override fun copy(args: CopyArgs.Tidslinje) = when (args) {
                is CopyArgs.Tidslinje.Full -> this.copy()
                is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                    fraOgMed = args.periode.fraOgMed,
                    tilOgMed = args.periode.tilOgMed,
                )
            }
        }
    }

    enum class LinjeStatus {
        OPPHØR,
        STANS,
        REAKTIVERING;
    }
}
