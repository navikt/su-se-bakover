package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.Beregning
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.firstDayOfNextMonth
import java.util.UUID

data class Oppdrag(
    val id: UUID30,
    val opprettet: Tidspunkt,
    val sakId: UUID,
    private val utbetalinger: List<Utbetaling> = listOf()
) {
    fun sisteOversendteUtbetaling(): Utbetaling? = oversendteUtbetalinger().lastOrNull()

    /**
     * Returnerer alle utbetalinger som tilhører oppdraget i den rekkefølgen de er opprettet.
     *
     * Uavhengig om de er oversendt/prøvd oversendt/kvitter ok eller kvittert feil.
     */
    fun hentUtbetalinger(): List<Utbetaling> = utbetalinger
        .sortedBy { it.opprettet.instant }

    /**
     * Returnerer utbetalingene sortert økende etter tidspunktet de er sendt til oppdrag. Filtrer bort de som er kvittert feil.
     * TODO jah: Ved initialisering e.l. gjør en faktisk verifikasjon på at ref-verdier på utbetalingslinjene har riktig rekkefølge
     */
    fun oversendteUtbetalinger(): List<Utbetaling> =
        utbetalinger.filter { it is Utbetaling.OversendtUtbetaling || it is Utbetaling.KvittertUtbetaling && it.kvittering.erKvittertOk() }
            .sortedBy { it.opprettet.instant } // TODO potentially fix sorting

    fun harOversendteUtbetalingerEtter(value: LocalDate) = oversendteUtbetalinger()
        .flatMap { it.utbetalingslinjer }
        .any {
            it.tilOgMed.isEqual(value) || it.tilOgMed.isAfter(value)
        }

    fun genererUtbetaling(strategy: UtbetalingStrategy, fnr: Fnr): Utbetaling.UtbetalingForSimulering =
        when (strategy) {
            is UtbetalingStrategy.Stans -> Strategy().Stans(strategy.clock).generate(fnr)
            is UtbetalingStrategy.Ny -> Strategy().Ny().generate(strategy.beregning, fnr)
            is UtbetalingStrategy.Gjenoppta -> Strategy().Gjenoppta().generate(fnr)
        }

    sealed class UtbetalingStrategy {
        data class Stans(val clock: Clock = Clock.systemUTC()) : UtbetalingStrategy()
        data class Ny(val beregning: Beregning) : UtbetalingStrategy()
        object Gjenoppta : UtbetalingStrategy()
    }

    private open inner class Strategy {
        inner class Stans(private val clock: Clock = Clock.systemUTC()) : Strategy() {
            fun generate(fnr: Fnr): Utbetaling.UtbetalingForSimulering {
                val stansesFraOgMed = idag(clock).with(firstDayOfNextMonth()) // neste mnd eller umiddelbart?

                validate(harOversendteUtbetalingerEtter(stansesFraOgMed)) { "Det eksisterer ingen utbetalinger med tilOgMed dato større enn eller lik $stansesFraOgMed" }
                validate(Utbetaling.UtbetalingType.STANS != sisteOversendteUtbetaling()?.type) { "Kan ikke stanse utbetalinger som allerede er stanset" }

                val sisteOversendteUtbetalingslinje = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()
                    ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å stanse")
                val stansesTilOgMed = sisteOversendteUtbetalingslinje.tilOgMed

                return Utbetaling.UtbetalingForSimulering(
                    utbetalingslinjer = listOf(
                        Utbetalingslinje(
                            fraOgMed = stansesFraOgMed,
                            tilOgMed = stansesTilOgMed,
                            forrigeUtbetalingslinjeId = sisteOversendteUtbetalingslinje.id,
                            beløp = 0
                        )
                    ),
                    fnr = fnr,
                    type = Utbetaling.UtbetalingType.STANS
                )
            }
        }

        inner class Ny : Strategy() {
            fun generate(beregning: Beregning, fnr: Fnr): Utbetaling.UtbetalingForSimulering {
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
                    type = Utbetaling.UtbetalingType.NY
                )
            }

            private fun createUtbetalingsperioder(beregning: Beregning) = beregning.månedsberegninger
                .groupBy { it.beløp }
                .map {
                    Utbetalingsperiode(
                        fraOgMed = it.value.minByOrNull { it.fraOgMed }!!.fraOgMed,
                        tilOgMed = it.value.maxByOrNull { it.tilOgMed }!!.tilOgMed,
                        beløp = it.key,
                    )
                }
        }

        inner class Gjenoppta : Strategy() {
            fun generate(fnr: Fnr): Utbetaling.UtbetalingForSimulering {
                val sisteOversendteUtbetalingslinje = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()
                    ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å gjenoppta")

                val stansetFraOgMed = sisteOversendteUtbetalingslinje.fraOgMed
                val stansetTilOgMed = sisteOversendteUtbetalingslinje.tilOgMed

                // Vi må ekskludere alt før nest siste stopp-utbetaling for ikke å duplisere utbetalinger.
                val startIndeks = oversendteUtbetalinger().dropLast(1).indexOfLast {
                    it.type == Utbetaling.UtbetalingType.STANS
                }.let { if (it < 0) 0 else it + 1 } // Ekskluderer den eventuelle stopp-utbetalingen

                val stansetEllerDelvisStansetUtbetalingslinjer = oversendteUtbetalinger()
                    .subList(
                        startIndeks,
                        oversendteUtbetalinger().size - 1
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
                    type = Utbetaling.UtbetalingType.GJENOPPTA
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
