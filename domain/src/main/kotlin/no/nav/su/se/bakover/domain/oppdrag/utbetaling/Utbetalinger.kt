package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.IngenUtbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.tidslinje
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.LocalDate

/**
 * Alltid sortert.
 * Ved insert/read fra databasen er utbetalinger alltid av typen [Utbetaling.OversendtUtbetaling], men ved opprettelse av ny utbetaling vil vi legge den til i saken som [Utbetaling.SimulertUtbetaling].
 */
data class Utbetalinger(
    // TODO jah: Vurder om denne kan være kun oversendte utbetalinger.
    private val utbetalinger: List<Utbetaling> = emptyList(),
) : List<Utbetaling> by utbetalinger {

    constructor(vararg utbetalinger: Utbetaling) : this(utbetalinger.toList())
    constructor(vararg utbetalinger: List<Utbetaling>) : this(utbetalinger.toList().flatten())
    constructor() : this(emptyList())

    init {
        sjekkDuplikateIder()
        sjekkDuplikateOpprettet()
        sjekkSortering()
        sjekkDenFørsteUtbetalingslinja()
        sjekkEndringslinjer()
        sjekkNyeLinjer()
    }

    companion object {
        /**
         * Sorterer utbetalingene etter tidspunkt og forrige-referansene i utbetalingslinjene.
         */
//        fun fraUsorterteUtbetalinger(@Suppress("UNUSED_PARAMETER") utbetalinger: List<Utbetaling>): Utbetalinger {
//            TODO() // Utbetalinger(utbetalinger.sortedBy { it.opprettet })
//        }
    }

    val utbetalingslinjer get() = utbetalinger.flatMap { it.utbetalingslinjer }
    val utbetalingslinjerAvTypenOpphør get() = utbetalingslinjer.filterIsInstance<Utbetalingslinje.Endring.Opphør>()
    val utbetalingslinjerAvTypenEndring get() = utbetalingslinjer.filterIsInstance<Utbetalingslinje.Endring>()
    val utbetalingslinjerAvTypenNy get() = utbetalingslinjer.filterIsInstance<Utbetalingslinje.Ny>()

    infix operator fun plus(utbetaling: Utbetaling): Utbetalinger {
        return Utbetalinger(utbetalinger + utbetaling)
    }

    fun filterNot(predicate: (Utbetaling) -> Boolean): Utbetalinger {
        return Utbetalinger(utbetalinger.filterNot(predicate))
    }

    fun kastHvisIkkeAlleErKvitterteUtenFeil() {
        require(utbetalinger.all { it is Utbetaling.OversendtUtbetaling.MedKvittering && it.kvittering.erKvittertOk() }) {
            "De fleste utbetalingsoperasjoner krever at alle utbetalinger er oversendt og vi har mottatt en OK-kvittering. Det er kun i stegene fram til vi sender og lagrer at vi har en blanding av utbetalinger som er oversendt og ikke. Som regel svarer økonomisystemet med en kvittering i løpet av sekunder. Et unntak er feilutbetalinger, da kan det ta dager."
        }
    }

    /**
     * Merk at denne svarer med false dersom dersom vi kun har et utbetaling på datoen, men ikke etter.
     */
    fun harUtbetalingerEtterDato(dato: LocalDate): Boolean {
        return utbetalingslinjer.any {
            it.periode.tilOgMed.isAfter(dato)
        }
    }

    fun harUtbetalingerEtterEllerPåDato(dato: LocalDate): Boolean {
        return utbetalingslinjer.any {
            it.periode.tilOgMed.isEqual(dato) || it.periode.tilOgMed.isAfter(dato)
        }
    }

    object FantIkkeGjeldendeUtbetaling

    fun hentGjeldendeUtbetaling(forDato: LocalDate): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
        return tidslinje().fold(
            { FantIkkeGjeldendeUtbetaling.left() },
            { it.gjeldendeForDato(forDato)?.right() ?: FantIkkeGjeldendeUtbetaling.left() },
        )
    }

    internal fun hentSisteUtbetalingslinje(): Utbetalingslinje? {
        return utbetalinger.lastOrNull()?.sisteUtbetalingslinje()
    }

    fun tidslinje(): Either<IngenUtbetalinger, TidslinjeForUtbetalinger> {
        return flatMap { it.utbetalingslinjer }.tidslinje()
    }

    private fun sjekkDuplikateIder() {
        this.map { it.id }.let {
            require(it.distinct() == it) {
                "Kan ikke inneholde duplikate utbetalinger. Fant duplikater for: ${
                it.groupingBy { it }.eachCount().filter { it.value > 1 }
                }"
            }
        }
    }

    private fun sjekkDenFørsteUtbetalingslinja() {
        utbetalinger.ifNotEmpty {
            utbetalinger.first().utbetalingslinjer.first().let {
                require(it is Utbetalingslinje.Ny) {
                    "Den første utbetalingslinjen for en sak må være av typen Ny."
                }
                require(it.forrigeUtbetalingslinjeId == null) {
                    "Den første utbetalingslinjen kan ikke ha forrigeUtbetalingslinjeId satt."
                }
            }
        }
    }

    private fun sjekkDuplikateOpprettet() {
        this.map { it.opprettet }.let {
            require(it.distinct().size == it.size) { "Utbetalinger må ha unike opprettet-tidspunkt, men var $it" }
        }
    }

    private fun sjekkSortering() {
        utbetalinger.map { it.opprettet }.let {
            require(it.sortedBy { it.instant } == it) {
                "Utbetalinger må være sortert i stigende rekkefølge, men var: $it"
            }
        }
        utbetalinger.map {
            Pair(
                it.utbetalingslinjer.map { it.opprettet.instant }.min(),
                it.utbetalingslinjer.map { it.opprettet.instant }.max(),
            )
        }.zipWithNext { a, b ->
            require(a.second < b.first) {
                "Alle opprettet tidspunktene i en utbetaling må ha skjedd etter alle opprettet tidspunktene i den forrige utbetalingen, men var: a(min,max): $a, b(min,max): $b"
            }
        }
    }

    private fun sjekkEndringslinjer() {
        sjekkEndringslinjerKobletMotNyLinje()

        val idTIlUtbetalingslinje = utbetalingslinjer.groupBy { it.id }
        idTIlUtbetalingslinje.values.filter { it.size > 1 }.forEach { utbetalingslinjer ->
            // Hver av disse vil være et sett med en NY og resterende endringer (ENDR)
            val head = utbetalingslinjer.first()
            require(head is Utbetalingslinje.Ny) {
                "Oppdaget ${utbetalingslinjer.size} av denne samme utbetalingslinjeIDen ${head.id} hvor den første ikke var NY, men var: ${head::class.simpleName}"
            }
            val tail = utbetalingslinjer.subList(1, utbetalingslinjer.size)
            require(tail.all { it is Utbetalingslinje.Endring }) {
                "Oppdaget ${utbetalingslinjer.size} av denne samme utbetalingslinjeIDen ${head.id} hvor de N siste elementene burde vært endring, men var: [${
                tail.joinToString {
                    """{"id": "${it.id}", "type": "${it::class.simpleName}"}"""
                }
                }]"
            }

            require(utbetalingslinjer.map { it.forrigeUtbetalingslinjeId }.distinct().size == 1) {
                "Oppdaget ${utbetalingslinjer.size} av denne samme utbetalingslinjeIDen ${head.id} hvor et eller flere elementer har forskjellig forrigeUtbetalingslinjeId: ${utbetalingslinjer.map { it.forrigeUtbetalingslinjeId }}"
            }

            utbetalingslinjer.zipWithNext { a, b ->
                require(a.opprettet <= b.opprettet) {
                    "Oppdaget ${utbetalingslinjer.size} av denne samme utbetalingslinjeIDen ${head.id} hvor et eller flere elementer er opprettet i feil rekkefølge: ${utbetalingslinjer.map { it.opprettet }}"
                }
            }
            utbetalingslinjer.zipWithNext { eldre, nyere ->
                when (nyere) {
                    is Utbetalingslinje.Endring.Opphør -> {
                        // Vi kan i praksis opphøre alle typer utbetalingslinjer (men vi burde kanskje ikke opphøre et opphør?)
                    }

                    is Utbetalingslinje.Endring.Reaktivering -> {
                        require(eldre is Utbetalingslinje.Endring.Stans) {
                            "Kan kun reaktivere en stans, men var: ${idTIlUtbetalingslinje[eldre.id]!!.last()::class.simpleName}"
                        }
                    }

                    is Utbetalingslinje.Endring.Stans -> {
                        require(eldre is Utbetalingslinje.Endring.Reaktivering || eldre is Utbetalingslinje.Ny) {
                            "Kan ikke stanse et opphør"
                        }
                    }

                    is Utbetalingslinje.Ny -> throw IllegalStateException("Har allerede verifisert at det ikke inneholder noen av disse.")
                }
            }
        }
    }

    private fun sjekkEndringslinjerKobletMotNyLinje() {
        utbetalingslinjerAvTypenEndring.forEach { e ->
            require(utbetalingslinjerAvTypenNy.any { it.id == e.id }) {
                "Endringslinje mangler en tilhørende NY linje for id: $e.id"
            }
        }
    }

    private fun sjekkNyeLinjer() {
        utbetalingslinjerAvTypenNy.reversed().zipWithNext { nyere, eldre ->
            require(nyere.opprettet >= eldre.opprettet) {
                // ideelt sett burde vi kunne sjekk > (istedenfor >=), men de ligger med samme opprettet-tidspunkt i databasen innenfor en utbetaling.
                "Den nyere utbetalingslinjen må ha opprettet-tidspunkt som er nyere eller samtidig som forrige linje, denne sjekken feilet: ${nyere.opprettet} >= ${eldre.opprettet}"
            }
            require(nyere.forrigeUtbetalingslinjeId == eldre.id) {
                "Den nyere utbetalingslinjen må ha forrigeUtbetalingslinjeId som er lik den eldre utbetalingslinjens id, denne sjekken feilet: ${nyere.forrigeUtbetalingslinjeId} == ${eldre.id}"
            }
            require(nyere.id != eldre.id) {
                "Den nyere utbetalingslinjen må ha en annen id enn den eldre utbetalingslinjen, denne sjekken feilet: ${nyere.id} != ${eldre.id}"
            }
        }
    }
}

private infix operator fun List<Utbetaling>.plus(utbetaling: Utbetaling): Utbetalinger {
    val result = ArrayList<Utbetaling>(size + 1)
    result.addAll(this)
    result.add(utbetaling)
    return Utbetalinger(result)
}

fun Sak.hentGjeldendeUtbetaling(
    forDato: LocalDate,
): Either<Utbetalinger.FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
    return this.utbetalinger.hentGjeldendeUtbetaling(forDato = forDato)
}
