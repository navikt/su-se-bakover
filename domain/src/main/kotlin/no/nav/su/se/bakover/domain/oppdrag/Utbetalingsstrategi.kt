package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.between
import no.nav.su.se.bakover.common.erFørsteDagIMåned
import no.nav.su.se.bakover.common.nonEmpty
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Uføregrunnlag.Companion.slåSammenPeriodeOgUføregrad
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
    abstract val sakstype: Sakstype

    /**
     * Sjekk om vi noen gang har forsøkt å opphøre ytelsen i perioden fra [datoForStanEllerReaktivering] til siste utbetaling.
     * Hvis dette er tilfelle kan vi ikke tillate stans av ytelsen med denne datoen, da en påfølgende reaktivering
     * i værste fall kan føre til dobbelt-utbetalinger.
     * TODO jm: Midlertidig sperre inntil TØB har fikset feilen, se https://jira.adeo.no/browse/TOB-1772
     */
    protected fun unngåBugMedReaktiveringAvOpphørIOppdrag(
        datoForStanEllerReaktivering: LocalDate,
    ): Either<Unit, Unit> {
        if (utbetalinger.flatMap { it.utbetalingslinjer }
            .filterIsInstance<Utbetalingslinje.Endring.Opphør>()
            .any {
                it.virkningsperiode.fraOgMed.between(
                        Periode.create(
                                fraOgMed = datoForStanEllerReaktivering,
                                tilOgMed = utbetalinger.maxOf { it.senesteDato() },
                            ),
                    )
            }
        ) {
            return Unit.left()
        }
        return Unit.right()
    }

    data class Stans(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalinger: List<Utbetaling>,
        override val behandler: NavIdentBruker,
        override val sakstype: Sakstype,
        val stansDato: LocalDate,
        val clock: Clock,
    ) : Utbetalingsstrategi() {

        fun generer(): Either<Feil, Utbetaling.UtbetalingForSimulering> {
            val sisteOversendteUtbetalingslinje =
                sisteOversendteUtbetaling()?.sisteUtbetalingslinje() ?: return Feil.FantIngenUtbetalinger.left()

            when {
                !harOversendteUtbetalingerEtter(stansDato) -> {
                    return Feil.IngenUtbetalingerEtterStansDato.left()
                }

                !(
                    LocalDate.now(clock).startOfMonth() == stansDato || LocalDate.now(clock).plusMonths(1)
                        .startOfMonth() == stansDato
                    ) -> {
                    return Feil.StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned.left()
                }

                sisteOversendteUtbetalingslinje is Utbetalingslinje.Endring.Stans -> {
                    return Feil.SisteUtbetalingErEnStans.left()
                }

                sisteOversendteUtbetalingslinje is Utbetalingslinje.Endring.Opphør -> {
                    return Feil.SisteUtbetalingErOpphør.left()
                }
                /**
                 * TODO jm: kan fjernes etter https://jira.adeo.no/browse/TOB-1772 er fikset.
                 */
                unngåBugMedReaktiveringAvOpphørIOppdrag(stansDato).isLeft() -> {
                    return Feil.KanIkkeStanseOpphørtePerioder.left()
                }
            }

            val opprettet = Tidspunkt.now(clock)
            return Utbetaling.UtbetalingForSimulering(
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Stans(
                        utbetalingslinje = sisteOversendteUtbetalingslinje,
                        virkningstidspunkt = stansDato,
                        clock = clock,
                        opprettet = opprettet,
                    ),
                ),
                type = Utbetaling.UtbetalingsType.STANS,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
                sakstype = sakstype,
            ).right()
        }

        sealed class Feil {
            object IngenUtbetalingerEtterStansDato : Feil()
            object StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned : Feil()
            object SisteUtbetalingErEnStans : Feil()
            object SisteUtbetalingErOpphør : Feil()
            object KanIkkeStanseOpphørtePerioder : Feil()
            object FantIngenUtbetalinger : Feil()
        }
    }

    data class NyUføreUtbetaling(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalinger: List<Utbetaling>,
        override val behandler: NavIdentBruker,
        override val sakstype: Sakstype,
        val beregning: Beregning,
        val clock: Clock,
        val uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        val kjøreplan: UtbetalingsinstruksjonForEtterbetalinger,
    ) : Utbetalingsstrategi() {
        fun generate(): Utbetaling.UtbetalingForSimulering {
            val opprettet = Tidspunkt.now(clock)

            val utbetalingslinjer = createUtbetalingsperioder(
                beregning,
                uføregrunnlag,
            ).map {
                Utbetalingslinje.Ny(
                    opprettet = opprettet,
                    fraOgMed = it.fraOgMed,
                    tilOgMed = it.tilOgMed,
                    forrigeUtbetalingslinjeId = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.id,
                    beløp = it.beløp,
                    uføregrad = it.uføregrad,
                    utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
                )
            }.let {
                Utbetalingshistorikk(
                    nyeUtbetalingslinjer = it,
                    eksisterendeUtbetalingslinjer = utbetalinger.flatMap { it.utbetalingslinjer },
                    clock = clock,
                ).generer()
            }

            check(
                utbetalingslinjer.filterIsInstance<Utbetalingslinje.Ny>()
                    .let { nyeLinjer ->
                        nyeLinjer.none { ny ->
                            ny.id in utbetalinger.flatMap { it.utbetalingslinjer }.map { it.id }
                        }
                    },
            ) { "Alle nye utbetalingslinjer skal ha ny id" }

            // TODO utbedre sjekker + legge til på andre strats
            check(
                utbetalingslinjer.filterIsInstance<Utbetalingslinje.Ny>()
                    .let { nyeLinjer ->
                        nyeLinjer.none { ny ->
                            nyeLinjer.minus(ny).any { it.forrigeUtbetalingslinjeId == ny.forrigeUtbetalingslinjeId }
                        }
                    },
            ) { "Alle nye utbetalingslinjer skal referere til forskjellig forrige utbetalingid" }

            check(
                sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.let { siste ->
                    utbetalingslinjer.singleOrNull { it.forrigeUtbetalingslinjeId == siste.id } != null
                } ?: true,
            ) { "Eksisterende utbetalinger skal være kjedet med nye utbetalinger" }

            return Utbetaling.UtbetalingForSimulering(
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = utbetalingslinjer.nonEmpty(),
                type = Utbetaling.UtbetalingsType.NY,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
                sakstype = sakstype,
            )
        }

        private fun createUtbetalingsperioder(
            beregning: Beregning,
            uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
        ): List<Utbetalingsperiode> {

            val slåttSammenMånedsberegninger = SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
                beregning.getMånedsberegninger(),
            ).beregningsperioder

            val slåttSammenUføregrunnlag =
                uføregrunnlag.slåSammenPeriodeOgUføregrad()

            val måneder = slåttSammenMånedsberegninger.map {
                it.periode
            }.flatMap {
                it.måneder()
            }.distinct()

            val uføregrunnlagPerioder = slåttSammenUføregrunnlag.map {
                it.first
            }.flatMap {
                it.måneder()
            }.distinct()

            val uføregrunnlagInneholderAlleMåneder = uføregrunnlagPerioder.containsAll(måneder)

            if (!uføregrunnlagInneholderAlleMåneder) {
                throw UtbetalingStrategyException("Uføregrunnlaget inneholder ikke alle beregningsperiodene. Grunnlagsperiodene: $uføregrunnlagPerioder, beregningsperiodene: $måneder")
            }

            return slåttSammenUføregrunnlag.flatMap { grunnlag ->
                slåttSammenMånedsberegninger.mapNotNull { månedsbereging ->
                    månedsbereging.periode.snitt(grunnlag.first)?.let {
                        Triple(
                            månedsbereging,
                            grunnlag.second,
                            it,
                        )
                    }
                }.map {
                    Utbetalingsperiode(
                        fraOgMed = it.third.fraOgMed,
                        tilOgMed = it.third.tilOgMed,
                        beløp = it.first.getSumYtelse(),
                        uføregrad = it.second,
                    )
                }
            }
        }
    }

    data class NyAldersUtbetaling(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalinger: List<Utbetaling>,
        override val behandler: NavIdentBruker,
        override val sakstype: Sakstype,
        val beregning: Beregning,
        val clock: Clock,
        val kjøreplan: UtbetalingsinstruksjonForEtterbetalinger,
    ) : Utbetalingsstrategi() {
        fun generate(): Utbetaling.UtbetalingForSimulering {
            val opprettet = Tidspunkt.now(clock)

            val utbetalingslinjer = SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
                beregning.getMånedsberegninger(),
            ).beregningsperioder.map {
                Utbetalingslinje.Ny(
                    opprettet = opprettet,
                    fraOgMed = it.periode.fraOgMed,
                    tilOgMed = it.periode.tilOgMed,
                    forrigeUtbetalingslinjeId = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.id,
                    beløp = it.getSumYtelse(),
                    uføregrad = null,
                    utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
                )
            }

            return Utbetaling.UtbetalingForSimulering(
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                utbetalingslinjer = NonEmptyList.fromListUnsafe(utbetalingslinjer),
                fnr = fnr,
                type = Utbetaling.UtbetalingsType.NY,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
                sakstype = sakstype,
            )
        }
    }

    data class Opphør(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalinger: List<Utbetaling>,
        override val behandler: NavIdentBruker,
        override val sakstype: Sakstype,
        val periode: Periode,
        val clock: Clock,
    ) : Utbetalingsstrategi() {
        fun generate(): Utbetaling.UtbetalingForSimulering {
            val sisteUtbetalingslinje = sisteOversendteUtbetaling()?.sisteUtbetalingslinje()?.also {
                validate(periode.fraOgMed.isBefore(it.tilOgMed)) { "Dato for opphør må være tidligere enn tilOgMed for siste utbetalingslinje" }
                validate(periode.fraOgMed.erFørsteDagIMåned()) { "Ytelse kan kun opphøres fra første dag i måneden" }
            } ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å opphøre")

            val opprettet = Tidspunkt.now(clock)

            return Utbetaling.UtbetalingForSimulering(
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = Utbetalingshistorikk(
                    nyeUtbetalingslinjer = listOf(
                        Utbetalingslinje.Endring.Opphør(
                            utbetalingslinje = sisteUtbetalingslinje,
                            virkningsperiode = periode,
                            opprettet = opprettet,
                            clock = clock,
                        ),
                    ),
                    eksisterendeUtbetalingslinjer = utbetalinger.flatMap { it.utbetalingslinjer },
                    clock = clock,
                ).generer().nonEmpty(),
                type = Utbetaling.UtbetalingsType.OPPHØR,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
                sakstype = sakstype,
            )
        }
    }

    data class Gjenoppta(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalinger: List<Utbetaling>,
        override val behandler: NavIdentBruker,
        override val sakstype: Sakstype,
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
            if (unngåBugMedReaktiveringAvOpphørIOppdrag(sisteOversendteUtbetalingslinje.periode.fraOgMed).isLeft()) return Feil.KanIkkeGjenopptaOpphørtePeriode.left()

            val opprettet = Tidspunkt.now(clock)
            return Utbetaling.UtbetalingForSimulering(
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Reaktivering(
                        utbetalingslinje = sisteOversendteUtbetalingslinje,
                        virkningstidspunkt = sisteOversendteUtbetalingslinje.virkningsperiode.fraOgMed,
                        opprettet = opprettet,
                        clock = clock,
                    ),
                ),
                type = Utbetaling.UtbetalingsType.GJENOPPTA,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
                sakstype = sakstype,
            ).right()
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
