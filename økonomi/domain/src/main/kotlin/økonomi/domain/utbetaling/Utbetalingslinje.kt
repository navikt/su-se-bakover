package økonomi.domain.utbetaling

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.LoggerFactory
import vilkår.uføre.domain.Uføregrad
import økonomi.domain.utbetaling.Utbetalingslinje.Endring.Opphør
import økonomi.domain.utbetaling.Utbetalingslinje.Endring.Reaktivering
import økonomi.domain.utbetaling.Utbetalingslinje.Endring.Stans
import java.time.Clock
import java.time.LocalDate

sealed interface Utbetalingslinje :
    PeriodisertInformasjon,
    Comparable<Utbetalingslinje> {
    val id: UUID30 // delytelseId

    // TODO jah: Fjern eller la den arve utbetalingen sin. Vurder samme med databasen.
    val opprettet: Tidspunkt
    val rekkefølge: Rekkefølge

    /**
     * @see originalFraOgMed
     */
    val fraOgMed: LocalDate

    /**
     * @see originalTilOgMed
     */
    val tilOgMed: LocalDate
    val forrigeUtbetalingslinjeId: UUID30?
    val beløp: Int
    val uføregrad: Uføregrad?
    val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger

    private val log get() = LoggerFactory.getLogger(this::class.java)

    /**
     * En utbetaling har [1-N] utbetalingslinjer.
     * Ved oversendelse til økonomisystemet vil rekkefølgen på linjene avgjøre hvilke som overskriver hverandre (siste har presedens).
     * Feltet [rekkefølge] tilsvarer denne rekkefølgen. Denne skal være unik per utbetaling.
     *
     * I tillegg har vi noen andre krav/garantier:
     * Alle linjer av typen NY vil kunne sammenlignes basert på forrigeUtbetalingslinjeId .
     * Når det kommer til ENDR-linjer, kan vi ikke bruke forrigeUtbetalingslinjeId (denne oversendes ikke til oppdrag ved ENDR), så her er vi avhengig av [rekkefølge].
     *
     * Dersom `id` er forskjellig, vil vi kunne sammenligne basert på forrigeUtbetalingslinjeId (en vil peke på den andre), dette skal også gjenspeiles i [rekkefølge].
     * Dersom `id` er lik (impliserer at det er 2 ENDR-linjer og at begge har lik id/forrigeUtbetalingslinjeId).
     */
    override fun compareTo(other: Utbetalingslinje): Int {
        return this.rekkefølge.compareTo(other.rekkefølge).also {
            when {
                it == 0 -> throw IllegalStateException("Kan ikke sammenligne linjer med samme rekkefølge.")
                it < 0 -> require(this.opprettet <= other.opprettet) {
                    "Utbetalingslinje.compareTo(...) feilet, this.rekkefølge (${this.rekkefølge} <= other.rekkefølge(${other.rekkefølge}, men this.opprettet (${this.opprettet}) > other.opprettet (${other.opprettet})"
                }

                else -> require(this.opprettet >= other.opprettet) {
                    "Utbetalingslinje.compareTo(...) feilet, this.rekkefølge (${this.rekkefølge} > other.rekkefølge(${other.rekkefølge}, men this.opprettet (${this.opprettet}) < other.opprettet (${other.opprettet})"
                }
            }
        }
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

    fun oppdaterReferanseTilForrigeUtbetalingslinje(id: UUID30?): Utbetalingslinje

    data class Ny(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt,
        override val rekkefølge: Rekkefølge,
        override val fraOgMed: LocalDate,
        override val tilOgMed: LocalDate,
        override val forrigeUtbetalingslinjeId: UUID30?,
        override val beløp: Int,
        override val uføregrad: Uføregrad?,
        override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
    ) : Utbetalingslinje {

        init {
            require(id != forrigeUtbetalingslinjeId) {
                "Utbetalingslinje sin id ($id) kan ikke være lik forrigeUtbetalingslinjeId"
            }
        }

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
    sealed interface Endring : Utbetalingslinje {
        val linjeStatus: LinjeStatus
        val virkningsperiode: Periode

        data class Opphør(
            override val id: UUID30,
            override val opprettet: Tidspunkt,
            override val rekkefølge: Rekkefølge,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override val forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningsperiode: Periode,
            override val uføregrad: Uføregrad?,
            override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
        ) : Endring {

            init {
                require(id != forrigeUtbetalingslinjeId) {
                    "Utbetalingslinje sin id ($id) kan ikke være lik forrigeUtbetalingslinjeId"
                }
            }

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
            override val rekkefølge: Rekkefølge,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override val forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningsperiode: Periode,
            override val uføregrad: Uføregrad?,
            override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
        ) : Endring {

            init {
                require(id != forrigeUtbetalingslinjeId) {
                    "Utbetalingslinje sin id ($id) kan ikke være lik forrigeUtbetalingslinjeId"
                }
            }

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
            override val rekkefølge: Rekkefølge,
            override val fraOgMed: LocalDate,
            override val tilOgMed: LocalDate,
            override val forrigeUtbetalingslinjeId: UUID30?,
            override val beløp: Int,
            override val virkningsperiode: Periode,
            override val uføregrad: Uføregrad?,
            override val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger = betalUtSåFortSomMulig,
        ) : Endring {

            init {
                require(id != forrigeUtbetalingslinjeId) {
                    "Utbetalingslinje sin id ($id) kan ikke være lik forrigeUtbetalingslinjeId"
                }
            }

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
        }
    }
}

fun List<Utbetalingslinje>.sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse() {
    this.filterIsInstance<Utbetalingslinje.Ny>().let {
        it.map { it.forrigeUtbetalingslinjeId }.ifNotEmpty {
            check(this.distinct() == this) { "Alle nye utbetalingslinjer skal referere til forskjellig forrige utbetalingid, men var: $this" }
        }
        it.map { it.id }.ifNotEmpty {
            check(this.distinct() == this) { "Alle nye utbetalingslinjer skal ha forskjellig id, men var: $this" }
        }
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
    val requiredStart = Rekkefølge.start()
    check(this.first().rekkefølge == requiredStart) {
        "Første linje må være Rekkefølge.start() som er: $requiredStart, men var ${this.first().rekkefølge}"
    }
    this.map { it.rekkefølge.value }.let {
        check(it == (requiredStart.value until (requiredStart.value + this.size)).toList()) {
            "Krever at rekkefølgen har en gitt start og er kontinuerlig, men var: $it"
        }
        check(it.distinct() == it) {
            "En utbetaling kan ikke ha utbetalingslinjer med duplikat rekkefølge: ${it - it.toSet()}"
        }
    }
}

fun List<Utbetalingslinje>.sjekkSammeForrigeUtbetalingsId() {
    this.groupBy { it.forrigeUtbetalingslinjeId }.forEach { map ->
        map.value.map { it.id }.let { ids ->
            check(ids.toSet().size == 1) {
                "To utbetalingslinjer med samme forrigeUtbetalingslinjeId, må også ha samme id. IDer: $ids, forrigeUtbetalingslinjeID: ${map.key}"
            }
        }
    }
}

fun List<Utbetalingslinje>.sjekkSammeUtbetalingsId() {
    this.groupBy { it.id }.forEach { map ->
        map.value.map { it.forrigeUtbetalingslinjeId }.let { forrigeUtbetalingslinjeIDer ->
            check(forrigeUtbetalingslinjeIDer.toSet().size == 1) {
                "To utbetalingslinjer med samme id, må også ha samme forrigeUtbetalingslinjeId. ID: ${map.key}, forrigeUtbetalingslinjeIDer: $forrigeUtbetalingslinjeIDer"
            }
        }
    }
}

fun List<Utbetalingslinje>.sjekkForrigeForNye() {
    this.zipWithNext { a, b ->
        if (b is Utbetalingslinje.Ny) {
            check(b.forrigeUtbetalingslinjeId == a.id) {
                "En ny utbetalingslinje (id: ${b.id}) sin forrigeUtbetalingslinjeId ${b.forrigeUtbetalingslinjeId} må peke på den forrige utbetalingslinjen (id: ${a.id}"
            }
            check(a.id != b.id) {
                "En ny utbetalingslinje (id: ${b.id}) må være forskjellig fra den forrige utbetalingslinjen sin id: ${a.id}"
            }
        }
    }
}
