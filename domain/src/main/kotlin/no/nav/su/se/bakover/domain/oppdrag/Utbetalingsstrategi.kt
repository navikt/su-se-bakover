package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import beregning.domain.Beregning
import beregning.domain.SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.between
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.uføre.domain.Uføregrunnlag.Companion.slåSammenPeriodeOgUføregrad
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.utbetaling.KunneIkkeGenerereUtbetalingsstrategiForStans
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.Utbetalingshistorikk
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import økonomi.domain.utbetaling.Utbetalingslinje
import økonomi.domain.utbetaling.Utbetalingsperiode
import økonomi.domain.utbetaling.sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse
import økonomi.domain.utbetaling.sjekkIngenNyeOverlapper
import økonomi.domain.utbetaling.sjekkSortering
import økonomi.domain.utbetaling.sjekkUnikOpprettet
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * @throws IllegalArgumentException dersom vi ikke har en OK kvittering for alle utbetalingene.
 */
sealed interface Utbetalingsstrategi {
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
    val eksisterendeUtbetalinger: Utbetalinger
    val behandler: NavIdentBruker
    val sakstype: Sakstype

    data class Stans(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val eksisterendeUtbetalinger: Utbetalinger,
        override val behandler: NavIdentBruker,
        override val sakstype: Sakstype,
        val stansDato: LocalDate,
        val clock: Clock,
        val aksepterKvitteringMedFeil: Boolean = false,
    ) : Utbetalingsstrategi {

        init {
            if (aksepterKvitteringMedFeil) {
                eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterte()
            } else {
                eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
            }
        }

        fun generer(): Either<KunneIkkeGenerereUtbetalingsstrategiForStans, Utbetaling.UtbetalingForSimulering> {
            val sisteOversendteUtbetalingslinje = eksisterendeUtbetalinger.sisteUtbetalingslinje()
                ?: return KunneIkkeGenerereUtbetalingsstrategiForStans.FantIngenUtbetalinger.left()

            when {
                !eksisterendeUtbetalinger.harUtbetalingerEtterEllerPåDato(stansDato) -> {
                    return KunneIkkeGenerereUtbetalingsstrategiForStans.IngenUtbetalingerEtterStansDato.left()
                }

                !(
                    LocalDate.now(clock).startOfMonth() == stansDato ||
                        LocalDate.now(clock).plusMonths(1)
                            .startOfMonth() == stansDato
                    ) -> {
                    return KunneIkkeGenerereUtbetalingsstrategiForStans.StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned.left()
                }

                sisteOversendteUtbetalingslinje is Utbetalingslinje.Endring.Stans -> {
                    return KunneIkkeGenerereUtbetalingsstrategiForStans.SisteUtbetalingErEnStans.left()
                }

                sisteOversendteUtbetalingslinje is Utbetalingslinje.Endring.Opphør -> {
                    return KunneIkkeGenerereUtbetalingsstrategiForStans.SisteUtbetalingErOpphør.left()
                }
                /**
                 * TODO jm: kan fjernes etter https://jira.adeo.no/browse/TOB-1772 er fikset.
                 */
                unngåBugMedReaktiveringAvOpphørIOppdrag(stansDato).isLeft() -> {
                    return KunneIkkeGenerereUtbetalingsstrategiForStans.KanIkkeStanseOpphørtePerioder.left()
                }
            }

            val opprettet = Tidspunkt.now(clock)
            return Utbetaling.UtbetalingForSimulering(
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                // TODO jah + jm: Jacob mener denne kan endres til å bruke [Utbetalingshistorikk] som f.eks. opphør
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Stans(
                        utbetalingslinjeSomSkalEndres = sisteOversendteUtbetalingslinje,
                        virkningstidspunkt = stansDato,
                        opprettet = opprettet,
                        rekkefølge = Rekkefølge.start(),
                    ),
                ),
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
                sakstype = sakstype,
            ).also {
                check(it.erStans()) { "Generert utbetaling er ikke en stans" }
                it.utbetalingslinjer.sjekkUnikOpprettet()
                it.utbetalingslinjer.sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse()
                it.utbetalingslinjer.sjekkSortering()
            }.right()
        }
    }

    /**
     * @param aksepterKvitteringMedFeil Dette er kun for å korrige en utbetaling som feilet.
     */
    data class NyUføreUtbetaling(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val eksisterendeUtbetalinger: Utbetalinger,
        override val behandler: NavIdentBruker,
        override val sakstype: Sakstype,
        val beregning: Beregning,
        val clock: Clock,
        val uføregrunnlag: List<Uføregrunnlag>,
        val kjøreplan: UtbetalingsinstruksjonForEtterbetalinger,
        val aksepterKvitteringMedFeil: Boolean = false,
    ) : Utbetalingsstrategi {

        init {
            if (aksepterKvitteringMedFeil) {
                eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterte()
            } else {
                eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
            }
        }

        fun generate(): Utbetaling.UtbetalingForSimulering {
            val opprettet = Tidspunkt.now(clock)
            val nesteUtbetalingstidspunkt = nesteTidspunktFunksjon(opprettet)
            val rekkefølgeGenerator = Rekkefølge.generator()

            val nyeUtbetalingslinjer = createUtbetalingsperioder(
                beregning = beregning,
                uføregrunnlag = uføregrunnlag,
            ).map {
                Utbetalingslinje.Ny(
                    opprettet = nesteUtbetalingstidspunkt(),
                    rekkefølge = rekkefølgeGenerator.neste(),
                    fraOgMed = it.fraOgMed,
                    tilOgMed = it.tilOgMed,
                    forrigeUtbetalingslinjeId = eksisterendeUtbetalinger.sisteUtbetalingslinje()?.id,
                    beløp = it.beløp,
                    uføregrad = it.uføregrad,
                    utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
                )
            }.toNonEmptyList().also {
                it.sjekkIngenNyeOverlapper()
                it.sjekkUnikOpprettet()
                it.sjekkSortering()
            }

            return Utbetaling.UtbetalingForSimulering(
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = Utbetalingshistorikk(
                    nyeUtbetalingslinjer = nyeUtbetalingslinjer,
                    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                    nesteUtbetalingstidspunkt = nesteUtbetalingstidspunkt,
                    rekkefølgeGenerator = rekkefølgeGenerator,
                    aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
                ).generer().toNonEmptyList(),
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
                sakstype = sakstype,
            ).also {
                it.utbetalingslinjer.sjekkUnikOpprettet()
                it.utbetalingslinjer.sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse()
                it.utbetalingslinjer.sjekkSortering()
            }
        }

        private fun createUtbetalingsperioder(
            beregning: Beregning,
            uføregrunnlag: List<Uføregrunnlag>,
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

    /**
     * @param aksepterKvitteringMedFeil Dette er kun for å korrige en utbetaling som feilet.
     */
    data class NyAldersUtbetaling(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val eksisterendeUtbetalinger: Utbetalinger,
        override val behandler: NavIdentBruker,
        override val sakstype: Sakstype,
        val beregning: Beregning,
        val clock: Clock,
        val kjøreplan: UtbetalingsinstruksjonForEtterbetalinger,
        val aksepterKvitteringMedFeil: Boolean = false,
    ) : Utbetalingsstrategi {

        init {
            if (aksepterKvitteringMedFeil) {
                eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterte()
            } else {
                eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
            }
        }

        fun generate(): Utbetaling.UtbetalingForSimulering {
            val opprettet = Tidspunkt.now(clock)
            val nesteUtbetalingstidspunkt = nesteTidspunktFunksjon(opprettet)
            val rekkefølgeGenerator = Rekkefølge.generator()
            val nyeUtbetalingslinjer = SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
                beregning.getMånedsberegninger(),
            ).beregningsperioder.map {
                Utbetalingslinje.Ny(
                    opprettet = nesteUtbetalingstidspunkt(),
                    rekkefølge = rekkefølgeGenerator.neste(),
                    fraOgMed = it.periode.fraOgMed,
                    tilOgMed = it.periode.tilOgMed,
                    forrigeUtbetalingslinjeId = eksisterendeUtbetalinger.sisteUtbetalingslinje()?.id,
                    beløp = it.getSumYtelse(),
                    uføregrad = null,
                    utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
                )
            }.also {
                it.sjekkIngenNyeOverlapper()
            }.toNonEmptyList()

            return Utbetaling.UtbetalingForSimulering(
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                utbetalingslinjer = Utbetalingshistorikk(
                    nyeUtbetalingslinjer = nyeUtbetalingslinjer,
                    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                    nesteUtbetalingstidspunkt = nesteUtbetalingstidspunkt,
                    rekkefølgeGenerator = rekkefølgeGenerator,
                    aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
                ).generer().toNonEmptyList(),
                fnr = fnr,
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
                sakstype = sakstype,
            ).also {
                it.utbetalingslinjer.sjekkUnikOpprettet()
                it.utbetalingslinjer.sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse()
                it.utbetalingslinjer.sjekkSortering()
            }
        }
    }

    data class Opphør(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val eksisterendeUtbetalinger: Utbetalinger,
        override val behandler: NavIdentBruker,
        override val sakstype: Sakstype,
        val periode: Periode,
        val clock: Clock,
        val aksepterKvitteringMedFeil: Boolean = false,
    ) : Utbetalingsstrategi {

        init {
            if (aksepterKvitteringMedFeil) {
                eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterte()
            } else {
                eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
            }
        }

        fun generate(): Utbetaling.UtbetalingForSimulering {
            val sisteUtbetalingslinje = eksisterendeUtbetalinger.sisteUtbetalingslinje()?.also {
                validate(periode.fraOgMed.isBefore(it.periode.tilOgMed)) { "Dato for opphør må være tidligere enn tilOgMed for siste utbetalingslinje" }
            } ?: throw UtbetalingStrategyException("Ingen oversendte utbetalinger å opphøre")

            val opprettet = Tidspunkt.now(clock)
            val nesteUtbetalingstidspunkt = nesteTidspunktFunksjon(opprettet)
            val rekkefølgeGenerator = Rekkefølge.generator()

            return Utbetaling.UtbetalingForSimulering(
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = Utbetalingshistorikk(
                    nyeUtbetalingslinjer = listOf(
                        Utbetalingslinje.Endring.Opphør(
                            utbetalingslinjeSomSkalEndres = sisteUtbetalingslinje,
                            virkningsperiode = periode,
                            opprettet = nesteUtbetalingstidspunkt(),
                            rekkefølge = rekkefølgeGenerator.neste(),
                        ),
                    ).toNonEmptyList(),
                    eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                    nesteUtbetalingstidspunkt = nesteUtbetalingstidspunkt,
                    rekkefølgeGenerator = rekkefølgeGenerator,
                    aksepterKvitteringMedFeil = aksepterKvitteringMedFeil,
                ).generer().toNonEmptyList(),
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
                sakstype = sakstype,
            ).also {
                it.utbetalingslinjer.sjekkUnikOpprettet()
                it.utbetalingslinjer.sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse()
                it.utbetalingslinjer.sjekkSortering()
            }
        }
    }

    data class Gjenoppta(
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val eksisterendeUtbetalinger: Utbetalinger,
        override val behandler: NavIdentBruker,
        override val sakstype: Sakstype,
        val clock: Clock,
        val aksepterKvitteringMedFeil: Boolean = false,
    ) : Utbetalingsstrategi {

        init {
            if (aksepterKvitteringMedFeil) {
                eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterte()
            } else {
                eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
            }
        }

        fun generer(): Either<Feil, Utbetaling.UtbetalingForSimulering> {
            val sisteOversendteUtbetalingslinje = eksisterendeUtbetalinger.sisteUtbetalingslinje()
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
                // TODO jah + jm: Jacob mener denne kan endres til å bruke [Utbetalingshistorikk] som f.eks. opphør
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Endring.Reaktivering(
                        utbetalingslinjeSomSkalEndres = sisteOversendteUtbetalingslinje,
                        virkningstidspunkt = sisteOversendteUtbetalingslinje.periode.fraOgMed,
                        opprettet = opprettet,
                        rekkefølge = Rekkefølge.start(),
                    ),
                ),
                behandler = behandler,
                avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
                sakstype = sakstype,
            ).also {
                check(it.erReaktivering()) { "Generert utbetaling er ikke en reaktivering" }
                it.utbetalingslinjer.sjekkUnikOpprettet()
                it.utbetalingslinjer.sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse()
                it.utbetalingslinjer.sjekkSortering()
            }.right()
        }

        sealed interface Feil {
            data object FantIngenUtbetalinger : Feil
            data object SisteUtbetalingErIkkeStans : Feil
            data object KanIkkeGjenopptaOpphørtePeriode : Feil
        }
    }

    class UtbetalingStrategyException(msg: String) : RuntimeException(msg)
}

/**
 * Øker med 1 mikrosekund per utbetalingslinje, siden postgressql ikke støtter nanosekunder.
 */
private fun nesteTidspunktFunksjon(utbetalingstidspunkt: Tidspunkt): () -> Tidspunkt {
    var currentTidspunkt = utbetalingstidspunkt

    return {
        currentTidspunkt.also { currentTidspunkt = it.plus(1, ChronoUnit.MICROS) }
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun validate(value: Boolean, lazyMessage: () -> Any) {
    contract {
        returns() implies value
    }
    if (!value) {
        val message = lazyMessage()
        throw Utbetalingsstrategi.UtbetalingStrategyException(message.toString())
    }
}

/**
 * Sjekk om vi noen gang har forsøkt å opphøre ytelsen i perioden fra [datoForStanEllerReaktivering] til siste utbetaling.
 * Hvis dette er tilfelle kan vi ikke tillate stans av ytelsen med denne datoen, da en påfølgende reaktivering
 * i værste fall kan føre til dobbelt-utbetalinger.
 * TODO jm: Midlertidig sperre inntil TØB har fikset feilen, se https://jira.adeo.no/browse/TOB-1772
 */
private fun Utbetalingsstrategi.unngåBugMedReaktiveringAvOpphørIOppdrag(
    datoForStanEllerReaktivering: LocalDate,
): Either<Unit, Unit> {
    if (eksisterendeUtbetalinger.utbetalingslinjerAvTypenOpphør
            .any {
                it.periode.fraOgMed.between(
                    Periode.create(
                        fraOgMed = datoForStanEllerReaktivering,
                        tilOgMed = eksisterendeUtbetalinger.maxOf { it.senesteDato() },
                    ),
                )
            }
    ) {
        return Unit.left()
    }
    return Unit.right()
}
