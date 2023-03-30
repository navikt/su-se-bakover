package no.nav.su.se.bakover.domain.oppdrag.avstemming

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.oppdrag.Fagområde
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import java.time.LocalDate

sealed class Avstemming {
    abstract val id: UUID30
    abstract val opprettet: Tidspunkt
    abstract val avstemmingXmlRequest: String?
    abstract val fagområde: Fagområde

    data class Grensesnittavstemming(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt,
        override val fagområde: Fagområde,
        override val avstemmingXmlRequest: String? = null,
        val fraOgMed: Tidspunkt,
        val tilOgMed: Tidspunkt,
        val utbetalinger: List<Utbetaling.OversendtUtbetaling>,
    ) : Avstemming() {
        init {
            if (utbetalinger.isNotEmpty()) {
                val byFagområde = utbetalinger
                    .map { it.sakstype.toFagområde() }
                    .groupBy { it }.keys
                require(byFagområde.count() == 1) {
                    "Avstemming kan kun gjøres for ett fagområde om gangen, fant utbetalinger for følgende fagområder: $byFagområde"
                }
                require(byFagområde.single() == fagområde) {
                    "Utbetalingsliste inneholder utbetalinger fra andre fagområder enn valgt fagområde: $fagområde"
                }
            }
        }
    }

    /**
     * Plukker ut løpende/aktive utbetalinger per sak/oppdrag fra [løpendeFraOgMed] og framover i tid.
     *
     * Algoritme for utplukk til konsistensavstemming.
     *  1.  Filtrer vekk alle utbetalinger som er opprettet senere enn [opprettetTilOgMed].
     *      Tidspunktet brukes til å informere OS om uttrekket vi har gjort, slik at de kan gjøre tilsvarende uttrekk.
     *  2.  Grupper alle utbetalingene per sak.
     *  3.  Slå sammen alle utbetalinger for en sak til en instans av [UtbetalingslinjerPerSak] som inneholder
     *      alle utbetalingslinjene for saken.
     *  4.  Lag tidslinje som inkluderer alle elementer i intervallet [løpendeFraOgMed] til [LocalDate.MAX] for alle utbetalinjene på hver sak.
     *      Filtrer vekk eventuelle opphør fra tidslinjen, da disse ikke regnes som "aktiv" i OS.
     *  5.  Filtrer vekk eventuelle saker som har 0 elementer igjen på tidslinjen etter at opphør er filtrert vekk.
     *      Dersom tidslinjen inneholder elementer, betyr dette at saken har "aktive" linjer som skal avstemmes.
     *  6.  Transformer de resterende utbetalingslinjene fra tidslinjen til en liste med deres id'er.
     *  7.  For hver instans av [UtbetalingslinjerPerSak], filtrer vekk alle utbetalingslinjer som ikke er av typen [Utbetalingslinje.Ny].
     *      Årsaken til dette er at stans/reak/opph er den samme linja (samme id), men med status satt, noe som vil føre til duplikater.
     *      Filtrer til slutt vekk alle utbetalingslinjene hvis id ikke eksisterer i listen fra 6.
     */
    sealed class Konsistensavstemming : Avstemming() {
        abstract override val id: UUID30
        abstract override val opprettet: Tidspunkt
        abstract override val fagområde: Fagområde
        abstract override val avstemmingXmlRequest: String?
        abstract val løpendeFraOgMed: Tidspunkt
        abstract val opprettetTilOgMed: Tidspunkt

        data class Ny(
            override val id: UUID30 = UUID30.randomUUID(),
            override val opprettet: Tidspunkt,
            override val løpendeFraOgMed: Tidspunkt,
            override val opprettetTilOgMed: Tidspunkt,
            override val fagområde: Fagområde,
            override val avstemmingXmlRequest: String? = null,
            private val utbetalinger: List<Utbetaling.OversendtUtbetaling>,
        ) : Konsistensavstemming() {

            init {
                if (utbetalinger.isNotEmpty()) {
                    val byFagområde = utbetalinger
                        .map { it.sakstype.toFagområde() }
                        .groupBy { it }.keys
                    require(byFagområde.count() == 1) {
                        "Avstemming kan kun gjøres for ett fagområde om gangen, fant utbetalinger for følgende fagområder: $byFagområde"
                    }
                    require(byFagområde.single() == fagområde) {
                        "Utbetalingsliste inneholder utbetalinger fra andre fagområder enn valgt fagområde: $fagområde"
                    }
                }
            }

            val løpendeUtbetalinger = utbetalinger
                .filter { it.opprettet <= opprettetTilOgMed.instant } // 1
                .groupBy { it.saksnummer } // 2
                .mapValues { entry -> // 3
                    UtbetalingslinjerPerSak(
                        saksnummer = entry.key,
                        utbetalinger = entry.value,
                    )
                }
                .mapValues { entry -> // 4
                    /**
                     * Dersom [løpendeFraOgMed] ikke er den første dagen i en måned, må denne justeres til å være det.
                     * Funksjonelt vil dette gi det samme resultatet som følge av at minste varighet på en [Periode]
                     * er 1 mnd - i praksis vil dette si at noe som er gyldig midt i en måned også er gyldig ved
                     * starten og slutten av samme måned.
                     */
                    entry.value to (
                        TidslinjeForUtbetalinger.fra(
                            utbetalinger = entry.value.utbetalinger,
                        )!!.krympTilPeriode(
                            fraOgMed = løpendeFraOgMed.toLocalDate(zoneIdOslo).startOfMonth(),
                        ) ?: emptyList()
                        ).filterNot {
                        it is UtbetalingslinjePåTidslinje.Opphør
                    }
                }
                .filterNot { it.value.second.isEmpty() } // 5
                .mapValues { pair -> pair.value.first to pair.value.second.map { it.kopiertFraId } } // 6
                .map { entry -> // 7
                    OppdragForKonsistensavstemming(
                        saksnummer = entry.value.first.saksnummer,
                        fagområde = entry.value.first.fagområde,
                        fnr = entry.value.first.fnr,
                        utbetalingslinjer = entry.value.first.utbetalingslinjer
                            .filterIsInstance<Utbetalingslinje.Ny>()
                            .filter { entry.value.second.contains(it.id) }
                            .map { it.toOppdragslinjeForKonsistensavstemming(entry.value.first.attestanter(it.id)) },
                    )
                }
        }

        data class Fullført(
            override val id: UUID30 = UUID30.randomUUID(),
            override val opprettet: Tidspunkt,
            override val løpendeFraOgMed: Tidspunkt,
            override val opprettetTilOgMed: Tidspunkt,
            override val fagområde: Fagområde,
            override val avstemmingXmlRequest: String? = null,
            val utbetalinger: Map<Saksnummer, List<Utbetalingslinje>>,
        ) : Konsistensavstemming()
    }
}

internal fun Utbetalingslinje.toOppdragslinjeForKonsistensavstemming(attestanter: NonEmptyList<NavIdentBruker>): OppdragslinjeForKonsistensavstemming {
    return OppdragslinjeForKonsistensavstemming(
        id = id,
        opprettet = opprettet,
        fraOgMed = originalFraOgMed(),
        tilOgMed = originalTilOgMed(),
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        beløp = beløp,
        attestanter = attestanter,
        utbetalingsinstruksjonForEtterbetalinger = utbetalingsinstruksjonForEtterbetalinger,
    )
}

private data class UtbetalingslinjerPerSak(
    val saksnummer: Saksnummer,
    val utbetalinger: List<Utbetaling.OversendtUtbetaling>,
) {
    val utbetalingslinjer = utbetalinger.flatMap { it.utbetalingslinjer }
    val fagområde = utbetalinger.first().sakstype.toFagområde()
    val fnr = utbetalinger.first().fnr
    fun attestanter(utbetalingslinjeId: UUID30): NonEmptyList<NavIdentBruker> {
        return utbetalinger
            .filter {
                it.utbetalingslinjer.any { it.id == utbetalingslinjeId }
            }
            .map { it.behandler }
            .toNonEmptyList()
    }
}

data class OppdragForKonsistensavstemming(
    val saksnummer: Saksnummer,
    val fagområde: Fagområde,
    val fnr: Fnr,
    val utbetalingslinjer: List<OppdragslinjeForKonsistensavstemming>,
)

data class OppdragslinjeForKonsistensavstemming(
    val id: UUID30,
    val opprettet: Tidspunkt,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    var forrigeUtbetalingslinjeId: UUID30?,
    val beløp: Int,
    val attestanter: NonEmptyList<NavIdentBruker>,
    val utbetalingsinstruksjonForEtterbetalinger: UtbetalingsinstruksjonForEtterbetalinger,
)

fun Sakstype.toFagområde(): Fagområde {
    return when (this) {
        Sakstype.ALDER -> Fagområde.SUALDER
        Sakstype.UFØRE -> Fagområde.SUUFORE
    }
}

fun Fagområde.toSakstype(): Sakstype {
    return when (this) {
        Fagområde.SUALDER -> Sakstype.ALDER
        Fagområde.SUUFORE -> Sakstype.UFØRE
    }
}
