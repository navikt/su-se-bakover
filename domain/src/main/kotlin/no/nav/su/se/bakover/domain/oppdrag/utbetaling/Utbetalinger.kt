package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import no.nav.su.se.bakover.common.UUID30
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalingslinje
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
        sjekkAlleLinjer()
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
        utbetalinger.filterNot { it is Utbetaling.OversendtUtbetaling.MedKvittering && it.kvittering.erKvittertOk() }
            .ifNotEmpty {
                val sakId = this.first().sakId
                val saksnummer = this.first().saksnummer
                val utbetalingsIDer = this.map { it.id }
                throw IllegalStateException(
                    "De fleste utbetalingsoperasjoner krever at alle utbetalinger er oversendt og vi har mottatt en OK-kvittering. " +
                        "Det er kun i stegene fram til vi sender og lagrer at vi har en blanding av utbetalinger som er oversendt og ikke. " +
                        "Som regel svarer økonomisystemet med en kvittering i løpet av sekunder. Et unntak er feilutbetalinger, da kan det ta dager. " +
                        "For sakId: $sakId, saksnummer: $saksnummer, utbetalingsIDer: $utbetalingsIDer",
                )
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

    data object FantIkkeGjeldendeUtbetaling

    fun sisteUtbetalingslinje(): Utbetalingslinje? {
        return sisteUtbetaling()?.sisteUtbetalingslinje()
    }

    fun sisteUtbetaling(): Utbetaling? {
        return utbetalinger.lastOrNull()
    }

    fun sisteUtbetalingslinjeId(): UUID30? {
        return sisteUtbetalingslinje()?.id
    }

    private fun sjekkDuplikateIder() {
        this.map { it.id }.let {
            check(it.distinct() == it) {
                "Kan ikke inneholde duplikate utbetalinger. Fant duplikater for: ${
                    it.groupingBy { it }.eachCount().filter { it.value > 1 }
                }"
            }
        }
    }

    private fun sjekkDenFørsteUtbetalingslinja() {
        utbetalinger.ifNotEmpty {
            utbetalinger.first().utbetalingslinjer.first().let {
                check(it is Utbetalingslinje.Ny) {
                    "Den første utbetalingslinjen for en sak må være av typen Ny."
                }
                check(it.forrigeUtbetalingslinjeId == null) {
                    "Den første utbetalingslinjen kan ikke ha forrigeUtbetalingslinjeId satt."
                }
            }
        }
    }

    private fun sjekkDuplikateOpprettet() {
        this.map { it.opprettet }.let {
            check(it.distinct().size == it.size) { "Utbetalinger må ha unike opprettet-tidspunkt, men var $it" }
        }
    }

    private fun sjekkSortering() {
        utbetalinger.map { it.opprettet }.let {
            check(it.sortedBy { it.instant } == it) {
                "Utbetalinger må være sortert i stigende rekkefølge, men var: $it"
            }
        }
        utbetalinger.map {
            Pair(
                it.utbetalingslinjer.map { it.opprettet.instant }.min(),
                it.utbetalingslinjer.map { it.opprettet.instant }.max(),
            )
        }.zipWithNext { a, b ->
            check(a.second < b.first) {
                "Alle opprettet tidspunktene i en utbetaling må ha skjedd etter alle opprettet tidspunktene i den forrige utbetalingen, men var: a(min,max): $a, b(min,max): $b"
            }
        }
    }

    private fun sjekkAlleLinjer() {
        utbetalingslinjer.zipWithNext { a, b ->
            // Regler må stemme overens med [Utbetalingsstrategi]
            if (b is Utbetalingslinje.Endring) {
                check(a.forrigeUtbetalingslinjeId == b.forrigeUtbetalingslinjeId && a.id == b.id) {
                    "Kan kun endre forrige linje. forrige linje: $a, endring: $b"
                }
            }
            when (b) {
                is Utbetalingslinje.Endring.Opphør,
                is Utbetalingslinje.Ny,
                -> Unit // Disse kan alltid følge etter en annen linje
                is Utbetalingslinje.Endring.Reaktivering -> {
                    check(a is Utbetalingslinje.Endring.Stans) {
                        "Kan ikke reaktivere en linje hvor forrige linje ikke er stanset. Linje: $a"
                    }
                }

                is Utbetalingslinje.Endring.Stans -> {
                    check(a is Utbetalingslinje.Ny || a is Utbetalingslinje.Endring.Reaktivering) {
                        "Kan ikke stanse en linje hvor forrige linje ikke er ny eller reaktivert. Linje: $a"
                    }
                }
            }
        }
    }

    private fun sjekkEndringslinjer() {
        sjekkEndringslinjerKobletMotNyLinje()

        val idTIlUtbetalingslinje = utbetalingslinjer.groupBy { it.id }
        idTIlUtbetalingslinje.values.filter { it.size > 1 }.forEach { utbetalingslinjer ->
            // Hver av disse vil være et sett med en NY og resterende endringer (ENDR)
            val head = utbetalingslinjer.first()
            check(head is Utbetalingslinje.Ny) {
                "Oppdaget ${utbetalingslinjer.size} av denne samme utbetalingslinjeIDen ${head.id} hvor den første ikke var NY, men var: ${head::class.simpleName}"
            }
            val tail = utbetalingslinjer.subList(1, utbetalingslinjer.size)
            check(tail.all { it is Utbetalingslinje.Endring }) {
                "Oppdaget ${utbetalingslinjer.size} av denne samme utbetalingslinjeIDen ${head.id} hvor de N siste elementene burde vært endring, men var: [${
                    tail.joinToString {
                        """{"id": "${it.id}", "type": "${it::class.simpleName}"}"""
                    }
                }]"
            }

            check(utbetalingslinjer.map { it.forrigeUtbetalingslinjeId }.distinct().size == 1) {
                "Oppdaget ${utbetalingslinjer.size} av denne samme utbetalingslinjeIDen ${head.id} hvor et eller flere elementer har forskjellig forrigeUtbetalingslinjeId: ${utbetalingslinjer.map { it.forrigeUtbetalingslinjeId }}"
            }

            utbetalingslinjer.zipWithNext { a, b ->
                check(a.opprettet <= b.opprettet) {
                    "Oppdaget ${utbetalingslinjer.size} av denne samme utbetalingslinjeIDen ${head.id} hvor et eller flere elementer er opprettet i feil rekkefølge: ${utbetalingslinjer.map { it.opprettet }}"
                }
            }

            utbetalingslinjer.filterIsInstance<Utbetalingslinje.Endring.Opphør>().ifNotEmpty {
                check(utbetalingslinjer.last() is Utbetalingslinje.Endring.Opphør) {
                    "Oppdaget ${utbetalingslinjer.size} av denne samme utbetalingslinjeIDen ${head.id} med endringer etter et opphør: ${utbetalingslinjer.map { it::class.simpleName }}"
                }
            }
            utbetalingslinjer.zipWithNext { eldre, nyere ->
                when (nyere) {
                    is Utbetalingslinje.Endring.Opphør -> {
                        // Vi kan i praksis opphøre alle typer utbetalingslinjer (men vi burde kanskje ikke opphøre et opphør?)
                    }

                    is Utbetalingslinje.Endring.Reaktivering -> {
                        check(eldre is Utbetalingslinje.Endring.Stans) {
                            "Kan kun reaktivere en stans ($eldre), men var: $nyere"
                        }
                    }

                    is Utbetalingslinje.Endring.Stans -> {
                        check(eldre is Utbetalingslinje.Endring.Reaktivering || eldre is Utbetalingslinje.Ny) {
                            "Kan ikke stanse ($nyere) en stans ($eldre)"
                        }
                    }

                    is Utbetalingslinje.Ny -> throw IllegalStateException("Har allerede verifisert at det ikke inneholder noen av disse.")
                }
            }
        }
    }

    private fun sjekkEndringslinjerKobletMotNyLinje() {
        utbetalingslinjerAvTypenEndring.forEach { e ->
            check(utbetalingslinjerAvTypenNy.any { it.id == e.id }) {
                "Endringslinje mangler en tilhørende NY linje for id: $e.id"
            }
        }
    }

    private fun sjekkNyeLinjer() {
        utbetalingslinjerAvTypenNy.reversed().zipWithNext { nyere, eldre ->
            check(nyere.opprettet >= eldre.opprettet) {
                // ideelt sett burde vi kunne sjekk > (istedenfor >=), men de ligger med samme opprettet-tidspunkt i databasen innenfor en utbetaling.
                "Den nyere utbetalingslinjen må ha opprettet-tidspunkt som er nyere eller samtidig som forrige linje, denne sjekken feilet: ${nyere.opprettet} >= ${eldre.opprettet}"
            }
            check(nyere.forrigeUtbetalingslinjeId == eldre.id) {
                "Den nyere utbetalingslinjen må ha forrigeUtbetalingslinjeId som er lik den eldre utbetalingslinjens id, denne sjekken feilet: ${nyere.forrigeUtbetalingslinjeId} == ${eldre.id}"
            }
            check(nyere.id != eldre.id) {
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
