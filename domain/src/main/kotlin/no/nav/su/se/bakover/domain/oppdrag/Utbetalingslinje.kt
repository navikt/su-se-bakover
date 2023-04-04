package no.nav.su.se.bakover.domain.oppdrag

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje.Endring.Opphør
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje.Endring.Reaktivering
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje.Endring.Stans
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.time.LocalDate

sealed class Utbetalingslinje : PeriodisertInformasjon, Comparable<Utbetalingslinje> {
    abstract val id: UUID30 // delytelseId
    abstract val opprettet: Tidspunkt
    abstract val rekkefølge: Rekkefølge?

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

    override fun compareTo(other: Utbetalingslinje): Int {
        return this.opprettet.instant.compareTo(other.opprettet.instant)
    }

    /**
     * Original [fraOgMed] som ble satt da linjen ble oversendt til OS.
     * Brukes i all hovedsak som [Periode.fraOgMed] for [Ny] og nødvendig input-data til OS ved [Endring].
     */
    fun originalFraOgMed(): LocalDate = fraOgMed

    /**
     * Original [tilOgMed] som ble satt da linjen ble oversendt til OS.
     * Brukes i all hovedsak som [Periode.tilOgMed] for [Ny] og nødvendig input-data til OS ved [Endring].
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
        override val rekkefølge: Rekkefølge?,
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
     * Representerer en endring av en eksisterende linje.
     * Linjen som endres kan være hvilken som helst type av [Utbetalingslinje].
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
            override val rekkefølge: Rekkefølge?,
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
                utbetalingslinjeSomSkalEndres: Utbetalingslinje,
                virkningsperiode: Periode,
                clock: Clock,
                rekkefølge: Rekkefølge,
            ) : this(
                utbetalingslinjeSomSkalEndres = utbetalingslinjeSomSkalEndres,
                virkningsperiode = virkningsperiode,
                opprettet = Tidspunkt.now(clock),
                rekkefølge = rekkefølge,
            )

            constructor(
                utbetalingslinjeSomSkalEndres: Utbetalingslinje,
                virkningsperiode: Periode,
                opprettet: Tidspunkt,
                rekkefølge: Rekkefølge,
            ) : this(
                id = utbetalingslinjeSomSkalEndres.id,
                opprettet = opprettet,
                rekkefølge = rekkefølge,
                fraOgMed = utbetalingslinjeSomSkalEndres.originalFraOgMed(),
                tilOgMed = utbetalingslinjeSomSkalEndres.originalTilOgMed(),
                forrigeUtbetalingslinjeId = utbetalingslinjeSomSkalEndres.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinjeSomSkalEndres.beløp,
                virkningsperiode = virkningsperiode,
                uføregrad = utbetalingslinjeSomSkalEndres.uføregrad,
                utbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
            )
        }

        data class Stans(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val rekkefølge: Rekkefølge?,
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

            /**
             * @param utbetalingslinjeSomSkalEndres er linjen som skal stanses. Gjenbruker bl.a. fraOgMed, tilOgMed og beløp.
             * @param virkningstidspunkt er datoen som linjen skal stanses fra. Må være den første i måneden.
             * @param clock brukes kun internt for å holde styr på rekkefølgen (erstattes av rekkefølge)
             * @param rekkefølge brukes kun internt for å holde styr på rekkefølgen (erstatter opprettet)
             */
            @TestOnly
            constructor(
                utbetalingslinjeSomSkalEndres: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
                clock: Clock,
                rekkefølge: Rekkefølge,
            ) : this(
                utbetalingslinjeSomSkalEndres = utbetalingslinjeSomSkalEndres,
                virkningstidspunkt = virkningstidspunkt,
                opprettet = Tidspunkt.now(clock),
                rekkefølge = rekkefølge,
            )

            /**
             * @param utbetalingslinjeSomSkalEndres er linjen som skal stanses. Gjenbruker bl.a. fraOgMed, tilOgMed og beløp. Denne kan tilhøre en tidligere utbetaling, slik at den ikke nødvendigvis henger sammen med opprettet/rekkefølge.
             * @param virkningstidspunkt er datoen som linjen skal stanses fra. Må være den første i måneden.
             * @param opprettet brukes kun internt for å holde styr på rekkefølgen (erstattes av rekkefølge)
             * @param rekkefølge brukes kun internt for å holde styr på rekkefølgen (erstatter opprettet)
             */
            constructor(
                utbetalingslinjeSomSkalEndres: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
                opprettet: Tidspunkt,
                rekkefølge: Rekkefølge,
            ) : this(
                id = utbetalingslinjeSomSkalEndres.id,
                opprettet = opprettet,
                rekkefølge = rekkefølge,
                fraOgMed = utbetalingslinjeSomSkalEndres.originalFraOgMed(),
                tilOgMed = utbetalingslinjeSomSkalEndres.originalTilOgMed(),
                forrigeUtbetalingslinjeId = utbetalingslinjeSomSkalEndres.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinjeSomSkalEndres.beløp,
                virkningsperiode = Periode.create(
                    fraOgMed = virkningstidspunkt,
                    tilOgMed = utbetalingslinjeSomSkalEndres.originalTilOgMed(),
                ),
                uføregrad = utbetalingslinjeSomSkalEndres.uføregrad,
                utbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
            )
        }

        data class Reaktivering(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val rekkefølge: Rekkefølge?,
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

            /**
             * @param utbetalingslinjeSomSkalEndres er linjen som skal stanses. Gjenbruker bl.a. fraOgMed, tilOgMed og beløp.
             * @param virkningstidspunkt er datoen som linjen skal stanses fra. Må være den første i måneden.
             * @param clock brukes kun internt for å holde styr på rekkefølgen (erstattes av rekkefølge)
             * @param rekkefølge brukes kun internt for å holde styr på rekkefølgen (erstatter opprettet)
             */
            @TestOnly
            constructor(
                utbetalingslinjeSomSkalEndres: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
                clock: Clock,
                rekkefølge: Rekkefølge,
            ) : this(
                utbetalingslinjeSomSkalEndres = utbetalingslinjeSomSkalEndres,
                virkningstidspunkt = virkningstidspunkt,
                opprettet = Tidspunkt.now(clock),
                rekkefølge = rekkefølge,
            )

            /**
             * @param utbetalingslinjeSomSkalEndres er linjen som skal stanses. Gjenbruker bl.a. fraOgMed, tilOgMed og beløp.
             * @param virkningstidspunkt er datoen som linjen skal stanses fra. Må være den første i måneden.
             * @param opprettet brukes kun internt for å holde styr på rekkefølgen (erstattes av rekkefølge)
             * @param rekkefølge brukes kun internt for å holde styr på rekkefølgen (erstatter opprettet)
             */
            constructor(
                utbetalingslinjeSomSkalEndres: Utbetalingslinje,
                virkningstidspunkt: LocalDate,
                opprettet: Tidspunkt,
                rekkefølge: Rekkefølge,
            ) : this(
                id = utbetalingslinjeSomSkalEndres.id,
                opprettet = opprettet,
                rekkefølge = rekkefølge,
                fraOgMed = utbetalingslinjeSomSkalEndres.originalFraOgMed(),
                tilOgMed = utbetalingslinjeSomSkalEndres.originalTilOgMed(),
                forrigeUtbetalingslinjeId = utbetalingslinjeSomSkalEndres.forrigeUtbetalingslinjeId,
                beløp = utbetalingslinjeSomSkalEndres.beløp,
                virkningsperiode = Periode.create(
                    fraOgMed = virkningstidspunkt,
                    tilOgMed = utbetalingslinjeSomSkalEndres.originalTilOgMed(),
                ),
                uføregrad = utbetalingslinjeSomSkalEndres.uføregrad,
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
        }
    }
}

fun List<Utbetalingslinje>.sjekkAlleNyeLinjerHarForskjelligForrigeReferanse() {
    this.filterIsInstance<Utbetalingslinje.Ny>()
        .map { it.forrigeUtbetalingslinjeId }.ifNotEmpty {
            check(this.distinct() == this) { "Alle nye utbetalingslinjer skal referere til forskjellig forrige utbetalingid, men var: $this" }
        }
}

fun List<Utbetalingslinje>.sjekkSortering() {
    check(this.sorted() == this) { "Utbetalingslinjer er ikke sortert i stigende rekkefølge" }
}

fun List<Utbetalingslinje>.sjekkUnikOpprettet() {
    this.map { it.opprettet }.let {
        check(it.distinct().size == it.size) { "Utbetalingslinjer har ikke unik opprettet: $it" }
    }
}

fun List<Utbetalingslinje>.sjekkIngenNyeOverlapper() {
    check(!filterIsInstance<Utbetalingslinje.Ny>().harOverlappende()) { "Nye linjer kan ikke overlappe" }
}

fun List<Utbetalingslinje>.sjekkRekkefølge() {
    if (this.isEmpty()) return
    val rekkefølge = this.map { it.rekkefølge }
    if (rekkefølge.all { it == null }) return
    check(rekkefølge.all { it != null }) {
        "Alle eller ingen av utbetalingslinjene må ha rekkefølge. Var: $rekkefølge"
    }
    val requiredStart = Rekkefølge.start()
    check(this.first().rekkefølge == requiredStart) {
        "Første linje må være Rekkefølge.start() som er: $requiredStart, men var ${this.first().rekkefølge}"
    }
    this.map { it.rekkefølge!!.value }.let {
        check(it == (requiredStart.value until(requiredStart.value + this.size)).toList()) {
            "Krever at rekkefølgen har en gitt start og er kontinuerlig, men var: $it"
        }
    }
}
