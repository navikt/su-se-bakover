package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.erFørsteDagIMåned
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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

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

            val sisteOversendtUtbetaling = sisteOversendteUtbetaling()?.also {
                validate(Utbetaling.UtbetalingsType.STANS != it.type) { "Kan ikke stanse utbetalinger som allerede er stanset" }
                validate(Utbetaling.UtbetalingsType.OPPHØR != it.type) { "Kan ikke stanse utbetalinger som allerede er opphørt" }
            } ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å stanse")

            return Utbetaling.UtbetalingForSimulering(
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Stans(
                        utbetalingslinje = sisteOversendtUtbetaling.sisteUtbetalingslinje(),
                        virkningstidspunkt = stansesFraOgMed,
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
            val utbetalingslinjer = createUtbetalingsperioder(beregning).map {
                Utbetalingslinje.Ny(
                    fraOgMed = it.fraOgMed,
                    tilOgMed = it.tilOgMed,
                    forrigeUtbetalingslinjeId = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.id,
                    beløp = it.beløp,
                )
            }.also {
                it.zipWithNext { a, b -> b.link(a) }
            }
            return Utbetaling.UtbetalingForSimulering(
                sakId = sakId,
                saksnummer = saksnummer,
                utbetalingslinjer = NonEmptyList.fromListUnsafe(utbetalingslinjer),
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
                        fraOgMed = it.periode.fraOgMed,
                        tilOgMed = it.periode.tilOgMed,
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
            val sisteUtbetalingslinje = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.also {
                validate(opphørsDato.isBefore(it.tilOgMed)) { "Dato for opphør må være tidligere enn tilOgMed for siste utbetalingslinje" }
                validate(opphørsDato.erFørsteDagIMåned()) { "Ytelse kan kun opphøres fra første dag i måneden" }
            } ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å opphøre")

            return Utbetaling.UtbetalingForSimulering(
                sakId = sakId,
                saksnummer = saksnummer,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Opphør(
                        utbetalingslinje = sisteUtbetalingslinje,
                        virkningstidspunkt = opphørsDato,
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

            validate(sisteOversendteUtbetalingslinje is Utbetalingslinje.Endring.Stans) { "Siste utbetaling er ikke en stans, kan ikke gjenoppta." }

            return Utbetaling.UtbetalingForSimulering(
                sakId = sakId,
                saksnummer = saksnummer,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Reaktivering(
                        utbetalingslinje = sisteOversendteUtbetalingslinje,
                        virkningstidspunkt = sisteOversendteUtbetalingslinje.virkningstidspunkt,
                    ),
                ),
                fnr = fnr,
                type = Utbetaling.UtbetalingsType.GJENOPPTA,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock)),
            )
        }
    }

    @OptIn(ExperimentalContracts::class)
    protected inline fun validate(value: Boolean, lazyMessage: () -> Any) {
        contract {
            returns() implies value
        }
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
