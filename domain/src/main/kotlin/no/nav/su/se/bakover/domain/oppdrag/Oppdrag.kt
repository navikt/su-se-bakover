package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.firstDayOfNextMonth
import java.util.UUID

data class Oppdrag(
    val id: UUID30,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    private val utbetalinger: List<Utbetaling.OversendtUtbetaling> = emptyList()
) {
    private fun sisteOversendteUtbetaling(): Utbetaling? = hentOversendteUtbetalingerUtenFeil().lastOrNull()

    /**
     * Returnerer utbetalingene sortert økende etter tidspunktet de er sendt til oppdrag. Filtrer bort de som er kvittert feil.
     * TODO jah: Ved initialisering e.l. gjør en faktisk verifikasjon på at ref-verdier på utbetalingslinjene har riktig rekkefølge
     */
    fun hentOversendteUtbetalingerUtenFeil(): List<Utbetaling> =
        utbetalinger.filter { it is Utbetaling.OversendtUtbetaling.UtenKvittering || it is Utbetaling.OversendtUtbetaling.MedKvittering && it.kvittering.erKvittertOk() }
            .sortedBy { it.opprettet.instant } // TODO potentially fix sorting

    private fun harOversendteUtbetalingerEtter(value: LocalDate) = hentOversendteUtbetalingerUtenFeil()
        .flatMap { it.utbetalingslinjer }
        .any {
            it.tilOgMed.isEqual(value) || it.tilOgMed.isAfter(value)
        }

    // TODO: Returner Either istedet for å kaste?
    fun genererUtbetaling(strategy: UtbetalingStrategy, fnr: Fnr): Utbetaling.UtbetalingForSimulering =
        when (strategy) {
            is UtbetalingStrategy.Stans -> Strategy().Stans(
                behandler = strategy.behandler,
                clock = strategy.clock
            ).generate(fnr)
            is UtbetalingStrategy.Ny -> Strategy().Ny(
                behandler = strategy.behandler,
                beregning = strategy.beregning,
                clock = strategy.clock
            ).generate(fnr)
            is UtbetalingStrategy.Gjenoppta -> Strategy().Gjenoppta(
                behandler = strategy.behandler,
                clock = strategy.clock
            ).generate(fnr)
        }

    sealed class UtbetalingStrategy {
        abstract val behandler: NavIdentBruker

        data class Stans(
            override val behandler: NavIdentBruker,
            val clock: Clock = Clock.systemUTC()
        ) : UtbetalingStrategy()

        data class Ny(
            override val behandler: NavIdentBruker,
            val beregning: Beregning,
            val clock: Clock = Clock.systemUTC()
        ) : UtbetalingStrategy()

        data class Gjenoppta(
            override val behandler: NavIdentBruker,
            val clock: Clock = Clock.systemUTC()
        ) : UtbetalingStrategy()
    }

    private open inner class Strategy {
        inner class Stans(
            private val behandler: NavIdentBruker,
            private val clock: Clock = Clock.systemUTC()
        ) : Strategy() {
            fun generate(fnr: Fnr): Utbetaling.UtbetalingForSimulering {
                val stansesFraOgMed = idag(clock).with(firstDayOfNextMonth()) // neste mnd eller umiddelbart?

                validate(harOversendteUtbetalingerEtter(stansesFraOgMed)) { "Det eksisterer ingen utbetalinger med tilOgMed dato større enn eller lik $stansesFraOgMed" }
                validate(Utbetaling.UtbetalingsType.STANS != sisteOversendteUtbetaling()?.type) { "Kan ikke stanse utbetalinger som allerede er stanset" }

                val sisteOversendteUtbetalingslinje = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()
                    ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å stanse")
                val stansesTilOgMed = sisteOversendteUtbetalingslinje.tilOgMed

                return Utbetaling.UtbetalingForSimulering(
                    utbetalingslinjer = listOf(
                        Utbetalingslinje(
                            fraOgMed = stansesFraOgMed,
                            tilOgMed = stansesTilOgMed,
                            forrigeUtbetalingslinjeId = sisteOversendteUtbetalingslinje.id,
                            beløp = 0.0
                        )
                    ),
                    fnr = fnr,
                    type = Utbetaling.UtbetalingsType.STANS,
                    oppdragId = id,
                    behandler = behandler,
                    avstemmingsnøkkel = Avstemmingsnøkkel(now(clock))
                )
            }
        }

        inner class Ny(
            private val behandler: NavIdentBruker,
            private val beregning: Beregning,
            private val clock: Clock = Clock.systemUTC()
        ) : Strategy() {
            fun generate(fnr: Fnr): Utbetaling.UtbetalingForSimulering {
                return Utbetaling.UtbetalingForSimulering(
                    utbetalingslinjer = createUtbetalingsperioder(beregning).map {
                        Utbetalingslinje(
                            fraOgMed = it.fraOgMed,
                            tilOgMed = it.tilOgMed,
                            forrigeUtbetalingslinjeId = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.id,
                            beløp = it.beløp
                        )
                    }.also {
                        it.zipWithNext { a, b -> b.link(a) }
                    },
                    fnr = fnr,
                    type = Utbetaling.UtbetalingsType.NY,
                    oppdragId = id,
                    behandler = behandler,
                    avstemmingsnøkkel = Avstemmingsnøkkel(now(clock))
                )
            }

            private fun createUtbetalingsperioder(beregning: Beregning) = beregning.månedsberegninger()
                .groupBy { it.sum() }
                .map {
                    Utbetalingsperiode(
                        fraOgMed = it.value.map { v -> v.periode().fraOgMed() }.minOrNull()!!,
                        tilOgMed = it.value.map { v -> v.periode().tilOgMed() }.maxOrNull()!!,
                        beløp = it.key
                    )
                }
        }

        inner class Gjenoppta(
            private val behandler: NavIdentBruker,
            private val clock: Clock = Clock.systemUTC()
        ) : Strategy() {
            fun generate(fnr: Fnr): Utbetaling.UtbetalingForSimulering {
                val sisteOversendteUtbetalingslinje = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()
                    ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å gjenoppta")

                val stansetFraOgMed = sisteOversendteUtbetalingslinje.fraOgMed
                val stansetTilOgMed = sisteOversendteUtbetalingslinje.tilOgMed

                // Vi må ekskludere alt før nest siste stopp-utbetaling for ikke å duplisere utbetalinger.
                val startIndeks = hentOversendteUtbetalingerUtenFeil().dropLast(1).indexOfLast {
                    it.type == Utbetaling.UtbetalingsType.STANS
                }.let { if (it < 0) 0 else it + 1 } // Ekskluderer den eventuelle stopp-utbetalingen

                val stansetEllerDelvisStansetUtbetalingslinjer = hentOversendteUtbetalingerUtenFeil()
                    .subList(
                        startIndeks,
                        hentOversendteUtbetalingerUtenFeil().size - 1
                    ) // Ekskluderer den siste stopp-utbetalingen
                    .flatMap {
                        it.utbetalingslinjer
                    }.filter {
                        // Merk: En utbetalingslinje kan være delvis stanset.
                        it.fraOgMed.between(
                            stansetFraOgMed,
                            stansetTilOgMed
                        ) || it.tilOgMed.between(
                            stansetFraOgMed,
                            stansetTilOgMed
                        )
                    }

                validate(stansetEllerDelvisStansetUtbetalingslinjer.isNotEmpty()) { "Kan ikke gjenoppta utbetaling. Fant ingen utbetalinger som kan gjenopptas i perioden: $stansetFraOgMed-$stansetTilOgMed" }
                validate(stansetEllerDelvisStansetUtbetalingslinjer.last().tilOgMed == stansetTilOgMed) {
                    "Feil ved start av utbetalinger. Stopputbetalingens tilOgMed ($stansetTilOgMed) matcher ikke utbetalingslinja (${stansetEllerDelvisStansetUtbetalingslinjer.last().tilOgMed}"
                }

                return Utbetaling.UtbetalingForSimulering(
                    utbetalingslinjer = stansetEllerDelvisStansetUtbetalingslinjer.fold(listOf()) { acc, utbetalingslinje ->
                        (
                            acc + Utbetalingslinje(
                                fraOgMed = maxOf(stansetFraOgMed, utbetalingslinje.fraOgMed),
                                tilOgMed = utbetalingslinje.tilOgMed,
                                forrigeUtbetalingslinjeId = acc.lastOrNull()?.id ?: sisteOversendteUtbetalingslinje.id,
                                beløp = utbetalingslinje.beløp
                            )
                            )
                    },
                    fnr = fnr,
                    type = Utbetaling.UtbetalingsType.GJENOPPTA,
                    oppdragId = id,
                    behandler = behandler,
                    avstemmingsnøkkel = Avstemmingsnøkkel(now(clock))
                )
            }
        }
    }

    private fun validate(value: Boolean, lazyMessage: () -> Any) {
        if (!value) {
            val message = lazyMessage()
            throw UtbetalingStrategyException(message.toString())
        }
    }
}

class UtbetalingStrategyException(msg: String) : RuntimeException(msg)
