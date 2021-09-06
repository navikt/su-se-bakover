package no.nav.su.se.bakover.domain.oppdrag.avstemming

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import java.time.LocalDate
import java.util.UUID

sealed class Avstemming {
    abstract val id: UUID30
    abstract val opprettet: Tidspunkt
    abstract val avstemmingXmlRequest: String?

    data class Grensesnittavstemming(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        val fraOgMed: Tidspunkt,
        val tilOgMed: Tidspunkt,
        val utbetalinger: List<Utbetaling.OversendtUtbetaling>,
        override val avstemmingXmlRequest: String? = null,
    ) : Avstemming()

    /**
     * Algoritme for utplukk til konsistensavstemming.
     *  1.  Filtrer vekk alle utbetalinger som er opprettet senere enn [opprettetTilOgMed].
     *      Tidspunktet brukes til å informere OS om uttrekket vi har gjort, slik at de kan gjøre tilsvarende uttrekk.
     *  2.  Grupper alle utbetalingene per sak.
     *  3.  Slå sammen alle utbetalinger for en sak til en instans av [OppdragForKonsistensavstemming] som inneholder
     *      alle utbetalingslinjene for saken.
     *  4.  Lag tidslinje som inkluderer alle elementer i intervallet [løpendeFraOgMed] til [LocalDate.MAX] for alle utbetalinjene på hver sak.
     *      Filtrer vekk eventuelle opphør fra tidslinjen, da disse ikke regnes som "aktiv" i OS.
     *  5.  Filtrer vekk eventuelle saker som har 0 elementer igjen på tidslinjen etter at opphør er filtrert vekk.
     *      Dersom tidslinjen inneholder elementer, betyr dette at saken har "aktive" linjer som skal avstemmes.
     *  6.  Transformer de resterende utbetalingslinjene fra tidslinjen til en liste med deres id'er.
     *  7.  For hver instans av [OppdragForKonsistensavstemming], filtrer vekk alle utbetalingslinjer som ikke er av typen [Utbetalingslinje.Ny].
     *      Årsaken til dette er at stans/reak/opph er den samme linja (samme id), men med status satt, noe som vil føre til duplikater.
     *      Filtrer til slutt vekk alle utbetalingslinjene hvis id ikke eksisterer i listen fra 6.
     */
    sealed class Konsistensavstemming : Avstemming() {
        abstract override val id: UUID30
        abstract override val opprettet: Tidspunkt
        abstract val løpendeFraOgMed: Tidspunkt
        abstract val opprettetTilOgMed: Tidspunkt
        abstract override val avstemmingXmlRequest: String?

        data class Ny(
            override val id: UUID30 = UUID30.randomUUID(),
            override val opprettet: Tidspunkt = Tidspunkt.now(),
            override val løpendeFraOgMed: Tidspunkt,
            override val opprettetTilOgMed: Tidspunkt,
            private val utbetalinger: List<Utbetaling.OversendtUtbetaling>,
            override val avstemmingXmlRequest: String? = null,
        ) : Konsistensavstemming() {

            val løpendeUtbetalinger = utbetalinger
                .filter { it.opprettet <= opprettetTilOgMed.instant } // 1
                .groupBy { it.saksnummer } // 2
                .mapValues { entry -> // 3
                    entry.value.map { utbetaling ->
                        OppdragForKonsistensavstemming(
                            id = utbetaling.id,
                            opprettet = utbetaling.opprettet,
                            sakId = utbetaling.sakId,
                            saksnummer = utbetaling.saksnummer,
                            fnr = utbetaling.fnr,
                            utbetalingslinjer = utbetaling.utbetalingslinjer,
                        )
                    }.reduce { acc, other ->
                        acc.copy(
                            utbetalingslinjer = acc.utbetalingslinjer + other.utbetalingslinjer,
                        )
                    }
                }
                .mapValues { entry -> // 4
                    entry.value to TidslinjeForUtbetalinger(
                        periode = Periode.create(
                            fraOgMed = løpendeFraOgMed.toLocalDate(zoneIdOslo),
                            tilOgMed = LocalDate.MAX, // Langt i framtiden
                        ),
                        utbetalingslinjer = entry.value.utbetalingslinjer,
                    ).tidslinje.filterNot {
                        it is UtbetalingslinjePåTidslinje.Opphør
                    }
                }
                .filterNot { it.value.second.isEmpty() } // 5
                .mapValues { pair -> pair.value.first to pair.value.second.map { it.kopiertFraId } } // 6
                .map { entry -> // 7
                    entry.value.first.copy(
                        utbetalingslinjer = entry.value.first.utbetalingslinjer
                            .filterIsInstance<Utbetalingslinje.Ny>()
                            .filter {
                                entry.value.second.contains(it.id)
                            },
                    )
                }
        }

        data class Fullført(
            override val id: UUID30 = UUID30.randomUUID(),
            override val opprettet: Tidspunkt = Tidspunkt.now(),
            override val løpendeFraOgMed: Tidspunkt,
            override val opprettetTilOgMed: Tidspunkt,
            val utbetalinger: Map<Saksnummer, List<Utbetalingslinje>>,
            override val avstemmingXmlRequest: String? = null,
        ) : Konsistensavstemming()
    }
}

data class OppdragForKonsistensavstemming(
    val id: UUID30,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val utbetalingslinjer: List<Utbetalingslinje>,
)
