package no.nav.su.se.bakover.domain.oppdrag

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.time.Clock
import java.time.LocalDate

sealed class Utbetalingslinje {
    abstract val id: UUID30 // delytelseId
    abstract val opprettet: Tidspunkt
    abstract val fraOgMed: LocalDate
    abstract val tilOgMed: LocalDate
    abstract var forrigeUtbetalingslinjeId: UUID30?
    abstract val beløp: Int
    abstract val periode: Periode
    abstract val uføregrad: Uføregrad?
    abstract val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger

    companion object {
        /** Vi ønsker bare å bruke kjøreplan ved etterbetaling i noen spesifikke tilfeller som f.eks. regulering. */
        private val betalUtSåFortSomMulig = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig
    }

    data class Ny(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt,
        override val fraOgMed: LocalDate,
        override val tilOgMed: LocalDate,
        override var forrigeUtbetalingslinjeId: UUID30?,
        override val beløp: Int,
        override val uføregrad: Uføregrad?,
        override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig
    ) : Utbetalingslinje() {

        @JsonIgnore
        override val periode = Periode.create(fraOgMed, tilOgMed)

        init {
            require(fraOgMed < tilOgMed) { "fraOgMed må være tidligere enn tilOgMed" }
        }

        fun link(other: Utbetalingslinje) {
            forrigeUtbetalingslinjeId = other.id
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
            override val uføregrad: Uføregrad?,
            override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig
        ) : Endring() {
            override val linjeStatus = LinjeStatus.OPPHØR

            @JsonIgnore
            override val periode = Periode.create(fraOgMed, tilOgMed)

            constructor(
                utbetalingslinje: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
                clock: Clock,
                opprettet: Tidspunkt = Tidspunkt.now(clock),
            ) : this(
                id = utbetalingslinje.id,
                opprettet = opprettet,
                fraOgMed = utbetalingslinje.fraOgMed,
                tilOgMed = utbetalingslinje.tilOgMed,
                forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinje.beløp,
                virkningstidspunkt = virkningstidspunkt,
                uføregrad = utbetalingslinje.uføregrad,
                utbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
            )
        }

        data class Stans(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override var forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningstidspunkt: LocalDate,
            override val uføregrad: Uføregrad?,
            override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig
        ) : Endring() {
            override val linjeStatus = LinjeStatus.STANS

            @JsonIgnore
            override val periode = Periode.create(fraOgMed, tilOgMed)

            constructor(
                utbetalingslinje: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
                clock: Clock,
                opprettet: Tidspunkt = Tidspunkt.now(clock)
            ) : this(
                id = utbetalingslinje.id,
                opprettet = opprettet,
                fraOgMed = utbetalingslinje.fraOgMed,
                tilOgMed = utbetalingslinje.tilOgMed,
                forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinje.beløp,
                virkningstidspunkt = virkningstidspunkt,
                uføregrad = utbetalingslinje.uføregrad,
                utbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
            )
        }

        data class Reaktivering(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override var forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningstidspunkt: LocalDate,
            override val uføregrad: Uføregrad?,
            override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig
        ) : Endring() {
            override val linjeStatus = LinjeStatus.REAKTIVERING

            @JsonIgnore
            override val periode = Periode.create(fraOgMed, tilOgMed)

            constructor(
                utbetalingslinje: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
                clock: Clock,
                opprettet: Tidspunkt = Tidspunkt.now(clock),
            ) : this(
                id = utbetalingslinje.id,
                opprettet = opprettet,
                fraOgMed = utbetalingslinje.fraOgMed,
                tilOgMed = utbetalingslinje.tilOgMed,
                forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinje.beløp,
                virkningstidspunkt = virkningstidspunkt,
                uføregrad = utbetalingslinje.uføregrad,
                utbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
            )
        }

        enum class LinjeStatus {
            OPPHØR,
            STANS,
            REAKTIVERING;
        }
    }
}

sealed class UtbetalingslinjePåTidslinje : KanPlasseresPåTidslinje<UtbetalingslinjePåTidslinje> {
    abstract val kopiertFraId: UUID30
    abstract override val opprettet: Tidspunkt
    abstract override val periode: Periode
    abstract val beløp: Int

    data class Ny(
        override val kopiertFraId: UUID30,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val beløp: Int,
    ) : UtbetalingslinjePåTidslinje() {
        override fun copy(args: CopyArgs.Tidslinje): Ny = when (args) {
            is CopyArgs.Tidslinje.Full -> this.copy()
            is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                periode = args.periode,
            )
            is CopyArgs.Tidslinje.Maskert -> {
                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
            }
        }
    }

    data class Stans(
        override val kopiertFraId: UUID30,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val beløp: Int = 0,
    ) : UtbetalingslinjePåTidslinje() {
        override fun copy(args: CopyArgs.Tidslinje): Stans = when (args) {
            is CopyArgs.Tidslinje.Full -> this.copy()
            is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                periode = args.periode,
            )
            is CopyArgs.Tidslinje.Maskert -> {
                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
            }
        }
    }

    data class Opphør(
        override val kopiertFraId: UUID30,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val beløp: Int = 0,
    ) : UtbetalingslinjePåTidslinje() {
        override fun copy(args: CopyArgs.Tidslinje): Opphør = when (args) {
            is CopyArgs.Tidslinje.Full -> this.copy()
            is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                periode = args.periode,
            )
            is CopyArgs.Tidslinje.Maskert -> {
                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
            }
        }
    }

    data class Reaktivering(
        override val kopiertFraId: UUID30,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val beløp: Int,
    ) : UtbetalingslinjePåTidslinje() {
        override fun copy(args: CopyArgs.Tidslinje): Reaktivering = when (args) {
            is CopyArgs.Tidslinje.Full -> this.copy()
            is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                periode = args.periode,
            )
            is CopyArgs.Tidslinje.Maskert -> {
                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
            }
        }
    }
}
