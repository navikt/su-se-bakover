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
                .filter { it.opprettet <= opprettetTilOgMed.instant }
                .groupBy { it.saksnummer }
                .mapValues { entry ->
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
                .mapValues { entry ->
                    entry.value to TidslinjeForUtbetalinger(
                        periode = Periode.create(
                            fraOgMed = løpendeFraOgMed.toLocalDate(zoneIdOslo),
                            tilOgMed = LocalDate.MAX, // Langt i framtiden
                        ),
                        utbetalingslinjer = entry.value.utbetalingslinjer,
                    ).tidslinje.filterNot {
                        it is UtbetalingslinjePåTidslinje.Opphør // Opphørte regnes ikke som aktive i OS
                    }
                }
                .filterNot { it.value.second.isEmpty() }
                .mapValues { pair -> pair.value.first to pair.value.second.map { it.kopiertFraId } }
                .map { entry ->
                    entry.value.first.copy(
                        utbetalingslinjer = entry.value.first.utbetalingslinjer.filter {
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
