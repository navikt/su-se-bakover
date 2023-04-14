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
    val utbetalinger: List<Utbetaling> = emptyList(),
) : List<Utbetaling> by utbetalinger {

    constructor(vararg utbetalinger: Utbetaling) : this(utbetalinger.toList())
    constructor(vararg utbetalinger: List<Utbetaling>) : this(utbetalinger.toList().flatten())
    constructor() : this(emptyList())

    init {
        this.map { it.id }.let {
            require(it.distinct() == it) {
                "Kan ikke inneholde duplikate utbetalinger. Fant duplikater for: ${it.groupingBy { it }.eachCount().filter { it.value > 1 }}"
            }
        }
        this.map { it.opprettet }.let {
            require(it.distinct().size == it.size) { "Utbetalinger må ha unike opprettet-tidspunkt, men var $it" }
            require(it.sortedBy { it.instant } == it) {
                "Utbetalinger må være sortert i stigende rekkefølge, men var: $it"
            }
        }
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

    infix operator fun plus(utbetaling: Utbetaling): Utbetalinger {
        return Utbetalinger(utbetalinger + utbetaling)
    }

    inline fun filterNot(predicate: (Utbetaling) -> Boolean): Utbetalinger {
        return Utbetalinger(utbetalinger.filterNot(predicate))
    }

    fun kastHvisIkkeAlleErKvitterteUtenFeil() {
        require(utbetalinger.all { it is Utbetaling.OversendtUtbetaling.MedKvittering && it.kvittering.erKvittertOk() }) {
            "De fleste utbetalingsoperasjoner krever at alle utbetalinger er oversendt og vi har mottatt en OK-kvittering. Det er kun i stegene fram til vi sender og lagrer at vi har en blanding av utbetalinger som er oversendt og ikke. Som regel svarer økonomisystemet med en kvittering i løpet av sekunder. Et unntak er feilutbetalinger, da kan det ta dager."
        }
    }

    /**
     * @throws IllegalArgumentException dersom vi ikke har en OK kvittering for alle utbetalingene.
     */
    internal fun harUtbetalingerEtter(date: LocalDate): Boolean {
        kastHvisIkkeAlleErKvitterteUtenFeil()
        return utbetalinger.flatMap { it.utbetalingslinjer }.let { utbetalingslinjer ->
            if (utbetalingslinjer.isNotEmpty()) {
                utbetalingslinjer.maxOf { it.periode.tilOgMed } > date
            } else {
                false
            }
        }
    }
    object FantIkkeGjeldendeUtbetaling

    fun hentGjeldendeUtbetaling(forDato: LocalDate): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
        return tidslinje().fold(
            { FantIkkeGjeldendeUtbetaling.left() },
            { it.gjeldendeForDato(forDato)?.right() ?: FantIkkeGjeldendeUtbetaling.left() },
        )
    }

    /**
     * @throws IllegalArgumentException dersom vi ikke har en OK kvittering for alle utbetalingene.
     */
    internal fun hentSisteUtbetalingslinje(): Utbetalingslinje? {
        kastHvisIkkeAlleErKvitterteUtenFeil()
        return utbetalinger.lastOrNull()?.sisteUtbetalingslinje()
    }

    fun tidslinje(): Either<IngenUtbetalinger, TidslinjeForUtbetalinger> {
        return flatMap { it.utbetalingslinjer }.tidslinje()
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
