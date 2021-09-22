package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.erFørsteDagIMåned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling.Companion.hentOversendteUtbetalingerUtenFeil
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import java.time.Clock
import java.time.LocalDate
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

    /**
     * Sjekk om vi noen gang har forsøkt å opphøre ytelsen i perioden fra [datoForStanEllerReaktivering] til siste utbetaling.
     * Hvis dette er tilfelle kan vi ikke tillate stans av ytelsen med denne datoen, da en påfølgende reaktivering
     * i værste fall kan føre til dobbelt-utbetalinger.
     * TODO jm: Midlertidig sperre inntil TØB har fikset feilen, se https://jira.adeo.no/browse/TOB-1772
     */
    protected fun unngåBugMedReaktiveringAvOpphørIOppdrag(
        datoForStanEllerReaktivering: LocalDate,
    ) {
        if (utbetalinger.flatMap { it.utbetalingslinjer }
                .filterIsInstance<Utbetalingslinje.Endring.Opphør>()
                .any {
                    it.virkningstidspunkt.between(
                        Periode.create(
                            fraOgMed = datoForStanEllerReaktivering,
                            tilOgMed = utbetalinger.maxOf { it.senesteDato() },
                        ),
                    )
                }
        ) {
            throw UtbetalingStrategyException("Kan ikke stanse utbetalinger for perioder hvor det eksisterer/har eksistert opphør.")
        }
    }

    data class Stans(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalinger: List<Utbetaling>,
        override val behandler: NavIdentBruker,
        val stansDato: LocalDate,
        val clock: Clock,
    ) : Utbetalingsstrategi() {
        override fun generate(): Utbetaling.UtbetalingForSimulering {
            validate(harOversendteUtbetalingerEtter(stansDato)) { "Det eksisterer ingen utbetalinger med tilOgMed dato større enn eller lik stansdato $stansDato" }
            validate(stansDato.erFørsteDagIMåned()) { "Dato for stans må være første dag i måneden" }
            validate(LocalDate.now(clock).plusMonths(1).startOfMonth() == stansDato.startOfMonth()) {
                "Dato for stans må være første dag i neste måned"
            }

            val sisteOversendtUtbetaling = sisteOversendteUtbetaling()?.also {
                validate(Utbetaling.UtbetalingsType.STANS != it.type) { "Kan ikke stanse utbetalinger som allerede er stanset" }
                validate(Utbetaling.UtbetalingsType.OPPHØR != it.type) { "Kan ikke stanse utbetalinger som allerede er opphørt" }
            } ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å stanse")

            /**
             * TODO jm: kan fjernes etter https://jira.adeo.no/browse/TOB-1772 er fikset.
             */
            unngåBugMedReaktiveringAvOpphørIOppdrag(
                datoForStanEllerReaktivering = stansDato,
            )

            return Utbetaling.UtbetalingForSimulering(
                opprettet = Tidspunkt.now(clock),
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Stans(
                        utbetalingslinje = sisteOversendtUtbetaling.sisteUtbetalingslinje(),
                        virkningstidspunkt = stansDato,
                        clock = clock,
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
                        clock = clock,
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
        fun generer(): Either<Feil, Utbetaling.UtbetalingForSimulering> {
            val sisteOversendteUtbetalingslinje = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()
                ?: return Feil.FantIngenUtbetalinger.left()

            if (sisteOversendteUtbetalingslinje !is Utbetalingslinje.Endring.Stans) return Feil.SisteUtbetalingErIkkeStans.left()

            /**
             * TODO jm: kan fjernes etter https://jira.adeo.no/browse/TOB-1772 er fikset.
             * I samme omgang må vi ta stilling til hvordan vi ønsker at dette skal fungerer for oss, spesielt i
             * tilfeller hvor perioden som gjenopptas inneholder opphør (skal alt etter denne datoen reaktiveres
             * uansett, eller skal kun et spesifikt opphør reaktivers). Default oppførsel er at kun match med dato reaktivers.
             */
            try {
                unngåBugMedReaktiveringAvOpphørIOppdrag(
                    datoForStanEllerReaktivering = sisteOversendteUtbetalingslinje.virkningstidspunkt,
                )
            } catch (ex: UtbetalingStrategyException) {
                return Feil.KanIkkeGjenopptaOpphørtePeriode.left()
            }

            return Utbetaling.UtbetalingForSimulering(
                sakId = sakId,
                saksnummer = saksnummer,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Reaktivering(
                        utbetalingslinje = sisteOversendteUtbetalingslinje,
                        virkningstidspunkt = sisteOversendteUtbetalingslinje.virkningstidspunkt,
                        clock = clock,
                    ),
                ),
                fnr = fnr,
                type = Utbetaling.UtbetalingsType.GJENOPPTA,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock)),
            ).right()
        }

        @Deprecated(message = "Erstattet for å kunne returnere Either", level = DeprecationLevel.ERROR)
        override fun generate(): Utbetaling.UtbetalingForSimulering {
            TODO("Skal ikke brukes. Fjern når det ikke lenger er bruk for abstrakt metode")
        }

        sealed class Feil {
            object FantIngenUtbetalinger : Feil()
            object SisteUtbetalingErIkkeStans : Feil()
            object KanIkkeGjenopptaOpphørtePeriode : Feil()
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
