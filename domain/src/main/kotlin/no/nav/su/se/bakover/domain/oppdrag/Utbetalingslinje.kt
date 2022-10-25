package no.nav.su.se.bakover.domain.oppdrag

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.time.Clock
import java.time.LocalDate

sealed class Utbetalingslinje : PeriodisertInformasjon {
    abstract val id: UUID30 // delytelseId
    abstract val opprettet: Tidspunkt

    /**
     * @see originalFraOgMed
     */
    protected abstract val fraOgMed: LocalDate

    /**
     * @see originalTilOgMed
     */
    protected abstract val tilOgMed: LocalDate
    abstract val forrigeUtbetalingslinjeId: UUID30?
    abstract val beløp: Int
    abstract val uføregrad: Uføregrad?
    abstract val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger

    /**
     * Original [fraOgMed] som ble satt da linjen ble oversendt til OS.
     * Brukes i all hovedsak som [periode.fraOgMed] for [Ny] og nødvendig input-data til OS ved [Endring].
     */
    fun originalFraOgMed(): LocalDate = fraOgMed

    /**
     * Original [tilOgMed] som ble satt da linjen ble oversendt til OS.
     * Brukes i all hovedsak som [periode.tilOgMed] for [Ny] og nødvendig input-data til OS ved [Endring].
     */
    fun originalTilOgMed(): LocalDate = tilOgMed

    /**
     * Representerer perioden hvor denne linjen har en betydning for oss.
     * [Ny] linjer vil alltid ha en periode tilsvarende [originalFraOgMed]-[originalTilOgMed], mens [Endring]
     * vil kunne ha en arbitrær periode.
     *
     * @see [Endring]
     */
    abstract override val periode: Periode

    companion object {
        /** Vi ønsker bare å bruke kjøreplan ved etterbetaling i noen spesifikke tilfeller som f.eks. regulering. */
        private val betalUtSåFortSomMulig = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig
    }

    abstract fun oppdaterReferanseTilForrigeUtbetalingslinje(id: UUID30?): Utbetalingslinje

    data class Ny(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt,
        override val fraOgMed: LocalDate,
        override val tilOgMed: LocalDate,
        override val forrigeUtbetalingslinjeId: UUID30?,
        override val beløp: Int,
        override val uføregrad: Uføregrad?,
        override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
    ) : Utbetalingslinje() {
        override fun oppdaterReferanseTilForrigeUtbetalingslinje(id: UUID30?): Ny {
            return copy(forrigeUtbetalingslinjeId = id)
        }

        @JsonIgnore
        override val periode = Periode.create(originalFraOgMed(), originalTilOgMed())
    }

    /**
     * Representerer en endring av en eksisterende linje. Linjen som endres kan være hvilken som helst type av
     * [Utbetalingslinje].
     *
     * @property linjeStatus angir hvilken type endring som skal gjennomføres; [Opphør], [Stans] eller [Reaktivering].
     * I OS er en endring en oppdatering av statusen på en linje.
     * @property virkningsperiode en syntetisert periode som forteller vårt system om hvilken periode denne endringen
     * gjelder for. Ved endring tilbyr OS kun muligheten for å sende én dato; denne representerer hvilken dato
     * endringen skal gjelde fra - i utgangspunktet vil alle endringer i OS gjøre seg gjeldende for perioden
     * ["angitt endringsdato" ([virkningsperiode.fraOgMed] for oss)]-[originalTilOgMed] for linjen som endres.
     */
    sealed class Endring : Utbetalingslinje() {
        abstract val linjeStatus: LinjeStatus
        protected abstract val virkningsperiode: Periode

        data class Opphør(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override val forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningsperiode: Periode,
            override val uføregrad: Uføregrad?,
            override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
        ) : Endring() {
            override val linjeStatus = LinjeStatus.OPPHØR

            override fun oppdaterReferanseTilForrigeUtbetalingslinje(id: UUID30?): Opphør {
                return copy(forrigeUtbetalingslinjeId = id)
            }

            @JsonIgnore
            override val periode = virkningsperiode

            constructor(
                utbetalingslinje: Utbetalingslinje,
                virkningsperiode: Periode,
                clock: Clock,
                opprettet: Tidspunkt = Tidspunkt.now(clock),
            ) : this(
                id = utbetalingslinje.id,
                opprettet = opprettet,
                fraOgMed = utbetalingslinje.originalFraOgMed(),
                tilOgMed = utbetalingslinje.originalTilOgMed(),
                forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinje.beløp,
                virkningsperiode = virkningsperiode,
                uføregrad = utbetalingslinje.uføregrad,
                utbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
            )
        }

        data class Stans(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override val forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningsperiode: Periode,
            override val uføregrad: Uføregrad?,
            override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
        ) : Endring() {
            override val linjeStatus = LinjeStatus.STANS

            override fun oppdaterReferanseTilForrigeUtbetalingslinje(id: UUID30?): Stans {
                return copy(forrigeUtbetalingslinjeId = id)
            }

            @JsonIgnore
            override val periode = virkningsperiode

            constructor(
                utbetalingslinje: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
                clock: Clock,
                opprettet: Tidspunkt = Tidspunkt.now(clock),
            ) : this(
                id = utbetalingslinje.id,
                opprettet = opprettet,
                fraOgMed = utbetalingslinje.originalFraOgMed(),
                tilOgMed = utbetalingslinje.originalTilOgMed(),
                forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinje.beløp,
                virkningsperiode = Periode.create(
                    fraOgMed = virkningstidspunkt,
                    tilOgMed = utbetalingslinje.originalTilOgMed(),
                ),
                uføregrad = utbetalingslinje.uføregrad,
                utbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
            )
        }

        data class Reaktivering(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override val forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningsperiode: Periode,
            override val uføregrad: Uføregrad?,
            override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
        ) : Endring() {
            override val linjeStatus = LinjeStatus.REAKTIVERING
            override fun oppdaterReferanseTilForrigeUtbetalingslinje(id: UUID30?): Reaktivering {
                return copy(forrigeUtbetalingslinjeId = id)
            }

            @JsonIgnore
            override val periode = virkningsperiode

            constructor(
                utbetalingslinje: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
                clock: Clock,
                opprettet: Tidspunkt = Tidspunkt.now(clock),
            ) : this(
                id = utbetalingslinje.id,
                opprettet = opprettet,
                fraOgMed = utbetalingslinje.originalFraOgMed(),
                tilOgMed = utbetalingslinje.originalTilOgMed(),
                forrigeUtbetalingslinjeId = utbetalingslinje.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinje.beløp,
                virkningsperiode = Periode.create(
                    fraOgMed = virkningstidspunkt,
                    tilOgMed = utbetalingslinje.originalTilOgMed(),
                ),
                uføregrad = utbetalingslinje.uføregrad,
                utbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
            )
        }

        enum class LinjeStatus {
            OPPHØR,
            STANS,
            REAKTIVERING,
            ;
        }
    }
}

sealed class UtbetalingslinjePåTidslinje : KanPlasseresPåTidslinje<UtbetalingslinjePåTidslinje> {
    abstract val kopiertFraId: UUID30
    abstract override val opprettet: Tidspunkt
    abstract override val periode: Periode
    abstract val beløp: Int

    /**
     * Ekvivalent i denne contexten betyr at linjen er av klasse, har samme [periode] og samme [beløp] som en annen linje.
     * Ekskluderer sjekk av [kopiertFraId] og [opprettet].
     */
    abstract fun ekvivalentMed(other: UtbetalingslinjePåTidslinje): Boolean

    data class Ny(
        override val kopiertFraId: UUID30,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val beløp: Int,
    ) : UtbetalingslinjePåTidslinje() {
        override fun ekvivalentMed(other: UtbetalingslinjePåTidslinje): Boolean {
            return other is Ny && periode == other.periode && beløp == other.beløp
        }

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
        override fun ekvivalentMed(other: UtbetalingslinjePåTidslinje): Boolean {
            return other is Stans && periode == other.periode && beløp == other.beløp
        }

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
        override fun ekvivalentMed(other: UtbetalingslinjePåTidslinje): Boolean {
            return other is Opphør && periode == other.periode && beløp == other.beløp
        }

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
        override fun ekvivalentMed(other: UtbetalingslinjePåTidslinje): Boolean {
            return other is Reaktivering && periode == other.periode && beløp == other.beløp
        }

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

fun List<Utbetalingslinje>.sjekkAlleNyeLinjerHarForskjelligForrigeReferanse() {
    check(
        this.filterIsInstance<Utbetalingslinje.Ny>()
            .map { it.forrigeUtbetalingslinjeId }
            .let { it.distinct() == it },
    ) { "Alle nye utbetalingslinjer skal referere til forskjellig forrige utbetalingid" }
}

fun List<Utbetalingslinje>.sjekkSortering() {
    check(this.sortedWith(utbetalingslinjeSortering) == this) { "Utbetalingslinjer er ikke sortert i stigende rekkefølge" }
}

fun List<Utbetalingslinje>.sjekkIngenNyeOverlapper() {
    check(!filterIsInstance<Utbetalingslinje.Ny>().harOverlappende()) { "Nye linjer kan ikke overlappe" }
}
