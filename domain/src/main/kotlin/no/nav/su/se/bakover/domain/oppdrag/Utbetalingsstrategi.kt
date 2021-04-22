package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.erFørsteDagIMåned
import no.nav.su.se.bakover.common.erSisteDagIMåned
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling.Companion.hentOversendteUtbetalingerUtenFeil
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters.firstDayOfNextMonth
import java.util.UUID

sealed class Utbetalingsstrategi {
    abstract val sakId: UUID
    abstract val saksnummer: Saksnummer
    abstract val fnr: Fnr
    abstract val utbetalinger: List<Utbetaling>
    abstract val behandler: NavIdentBruker
    abstract fun generate(): Utbetaling.UtbetalingForSimulering

    data class Stans(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalinger: List<Utbetaling>,
        override val behandler: NavIdentBruker,
        val clock: Clock,
    ) : Utbetalingsstrategi() {
        override fun generate(): Utbetaling.UtbetalingForSimulering {
            val stansesFraOgMed =
                idag(clock).with(firstDayOfNextMonth()) // TODO jah: Tor Erik ønsker at den skal stanses snarest mulig, men vi ønsker ikke å stanse ting som er sent til UR/er allerede utbetalt.

            validate(harOversendteUtbetalingerEtter(stansesFraOgMed)) { "Det eksisterer ingen utbetalinger med tilOgMed dato større enn eller lik $stansesFraOgMed" }
            validate(Utbetaling.UtbetalingsType.STANS != sisteOversendteUtbetaling()?.type) { "Kan ikke stanse utbetalinger som allerede er stanset" }

            val sisteOversendteUtbetalingslinje = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()
                ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å stanse")
            val stansesTilOgMed = sisteOversendteUtbetalingslinje.tilOgMed

            return Utbetaling.UtbetalingForSimulering(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    Utbetalingslinje.Ny(
                        fraOgMed = stansesFraOgMed,
                        tilOgMed = stansesTilOgMed,
                        forrigeUtbetalingslinjeId = sisteOversendteUtbetalingslinje.id,
                        beløp = 0,
                    ),
                ),
                type = Utbetaling.UtbetalingsType.STANS,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock)),
            )
        }
    }

    data class Ny(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalinger: List<Utbetaling>,
        override val behandler: NavIdentBruker,
        val beregning: Beregning,
        val clock: Clock,
    ) : Utbetalingsstrategi() {
        override fun generate(): Utbetaling.UtbetalingForSimulering {
            return Utbetaling.UtbetalingForSimulering(
                sakId = sakId,
                saksnummer = saksnummer,
                utbetalingslinjer = createUtbetalingsperioder(beregning).map {
                    Utbetalingslinje.Ny(
                        fraOgMed = it.fraOgMed,
                        tilOgMed = it.tilOgMed,
                        forrigeUtbetalingslinjeId = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.id,
                        beløp = it.beløp,
                    )
                }.also {
                    it.zipWithNext { a, b -> b.link(a) }
                },
                fnr = fnr,
                type = Utbetaling.UtbetalingsType.NY,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock)),
            )
        }

        private fun createUtbetalingsperioder(beregning: Beregning) =
            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(beregning.getMånedsberegninger()).beregningsperioder
                .map {
                    Utbetalingsperiode(
                        fraOgMed = it.getPeriode().fraOgMed,
                        tilOgMed = it.getPeriode().tilOgMed,
                        beløp = it.getSumYtelse(),
                    )
                }
    }

    data class Opphør(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalinger: List<Utbetaling>,
        override val behandler: NavIdentBruker,
        val opphørsDato: LocalDate,
        val clock: Clock,
    ) : Utbetalingsstrategi() {
        override fun generate(): Utbetaling.UtbetalingForSimulering {
            val sisteUtbetalingslinje = sisteOversendteUtbetaling()?.sisteUtbetalingslinje().let {
                validate(it is Utbetalingslinje) { "Sak: $sakId har ingen utbetalinger som kan opphøres" }
                it!!.let { sisteUtbetalingslinje ->
                    validate(opphørsDato.isAfter(LocalDate.now(clock))) { "Støtter kun opphør framover i tid" }
                    validate(opphørsDato.isBefore(sisteUtbetalingslinje.tilOgMed)) { "Dato for opphør må være tidligere enn tilOgMed for siste utbetalingslinje" }
                    validate(opphørsDato.erFørsteDagIMåned() || opphørsDato.erSisteDagIMåned()) { "Ytelse kan kun opphøres fra første eller siste dag i en måned." }
                }
                it
            }

            return Utbetaling.UtbetalingForSimulering(
                sakId = sakId,
                saksnummer = saksnummer,
                utbetalingslinjer = listOf(
                    Utbetalingslinje.Endring(
                        utbetalingslinje = sisteUtbetalingslinje,
                        statusendring = Utbetalingslinje.Statusendring(
                            status = Utbetalingslinje.LinjeStatus.OPPHØR,
                            fraOgMed = opphørsDato,
                        ),
                    ),
                ),
                fnr = fnr,
                type = Utbetaling.UtbetalingsType.OPPHØR,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock)),
            )
        }
    }

    data class Gjenoppta(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalinger: List<Utbetaling>,
        override val behandler: NavIdentBruker,
        val clock: Clock,
    ) : Utbetalingsstrategi() {
        override fun generate(): Utbetaling.UtbetalingForSimulering {
            val sisteOversendteUtbetalingslinje = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()
                ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å gjenoppta")

            val stansetFraOgMed = sisteOversendteUtbetalingslinje.fraOgMed
            val stansetTilOgMed = sisteOversendteUtbetalingslinje.tilOgMed

            // Vi må ekskludere alt før nest siste stopp-utbetaling for ikke å duplisere utbetalinger.
            val startIndeks = utbetalinger.hentOversendteUtbetalingerUtenFeil().dropLast(1).indexOfLast {
                it.type == Utbetaling.UtbetalingsType.STANS
            }.let { if (it < 0) 0 else it + 1 } // Ekskluderer den eventuelle stopp-utbetalingen

            val stansetEllerDelvisStansetUtbetalingslinjer = utbetalinger.hentOversendteUtbetalingerUtenFeil()
                .subList(
                    startIndeks,
                    utbetalinger.hentOversendteUtbetalingerUtenFeil().size - 1,
                ) // Ekskluderer den siste stopp-utbetalingen
                .flatMap {
                    it.utbetalingslinjer
                }.filter {
                    // Merk: En utbetalingslinje kan være delvis stanset.
                    it.fraOgMed.between(
                        stansetFraOgMed,
                        stansetTilOgMed,
                    ) || it.tilOgMed.between(
                        stansetFraOgMed,
                        stansetTilOgMed,
                    )
                }

            validate(stansetEllerDelvisStansetUtbetalingslinjer.isNotEmpty()) { "Kan ikke gjenoppta utbetaling. Fant ingen utbetalinger som kan gjenopptas i perioden: $stansetFraOgMed-$stansetTilOgMed" }
            validate(stansetEllerDelvisStansetUtbetalingslinjer.last().tilOgMed == stansetTilOgMed) {
                "Feil ved start av utbetalinger. Stopputbetalingens tilOgMed ($stansetTilOgMed) matcher ikke utbetalingslinja (${stansetEllerDelvisStansetUtbetalingslinjer.last().tilOgMed}"
            }

            return Utbetaling.UtbetalingForSimulering(
                sakId = sakId,
                saksnummer = saksnummer,
                utbetalingslinjer = stansetEllerDelvisStansetUtbetalingslinjer.fold(listOf()) { acc, utbetalingslinje ->
                    (
                        acc + Utbetalingslinje.Ny(
                            fraOgMed = maxOf(stansetFraOgMed, utbetalingslinje.fraOgMed),
                            tilOgMed = utbetalingslinje.tilOgMed,
                            forrigeUtbetalingslinjeId = acc.lastOrNull()?.id ?: sisteOversendteUtbetalingslinje.id,
                            beløp = utbetalingslinje.beløp,
                        )
                        )
                },
                fnr = fnr,
                type = Utbetaling.UtbetalingsType.GJENOPPTA,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock)),
            )
        }
    }

    protected fun validate(value: Boolean, lazyMessage: () -> Any) {
        if (!value) {
            val message = lazyMessage()
            throw UtbetalingStrategyException(message.toString())
        }
    }

    protected fun sisteOversendteUtbetaling(): Utbetaling? =
        utbetalinger.hentOversendteUtbetalingerUtenFeil().lastOrNull()

    protected fun harOversendteUtbetalingerEtter(value: LocalDate) = utbetalinger.hentOversendteUtbetalingerUtenFeil()
        .flatMap { it.utbetalingslinjer }
        .any {
            it.tilOgMed.isEqual(value) || it.tilOgMed.isAfter(value)
        }

    class UtbetalingStrategyException(msg: String) : RuntimeException(msg)
}
