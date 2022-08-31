package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.and
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

sealed interface Utbetaling {
    val id: UUID30
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
    val utbetalingslinjer: NonEmptyList<Utbetalingslinje>
    val behandler: NavIdentBruker
    val avstemmingsnøkkel: Avstemmingsnøkkel
    val sakstype: Sakstype

    fun sisteUtbetalingslinje() = utbetalingslinjer.last()

    fun erFørstegangsUtbetaling() = utbetalingslinjer.let { linjer ->
        linjer.any { it.forrigeUtbetalingslinjeId == null }
            // unngå at en eventuell endring av første utbetalingslinje noensinne oppfattes som førstegangsutbetaling
            .and { linjer.filterIsInstance<Utbetalingslinje.Endring>().none { it.forrigeUtbetalingslinjeId == null } }
    }

    fun tidligsteDato() = utbetalingslinjer.minOf { it.fraOgMed }
    fun senesteDato() = utbetalingslinjer.maxOf { it.tilOgMed }
    fun bruttoBeløp() = utbetalingslinjer.sumOf { it.beløp }

    fun erStans() = utbetalingslinjer.all { it is Utbetalingslinje.Endring.Stans }

    fun erReaktivering() = utbetalingslinjer.all { it is Utbetalingslinje.Endring.Reaktivering }

    data class UtbetalingForSimulering(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        override val behandler: NavIdentBruker,
        override val avstemmingsnøkkel: Avstemmingsnøkkel,
        override val sakstype: Sakstype,
    ) : Utbetaling {
        fun toSimulertUtbetaling(simulering: Simulering) =
            SimulertUtbetaling(
                this,
                simulering = simulering,
            )
    }

    data class SimulertUtbetaling(
        private val utbetalingForSimulering: UtbetalingForSimulering,
        val simulering: Simulering,
    ) : Utbetaling by utbetalingForSimulering {
        fun toOversendtUtbetaling(oppdragsmelding: Utbetalingsrequest) =
            OversendtUtbetaling.UtenKvittering(
                this,
                simulering = simulering,
                utbetalingsrequest = oppdragsmelding,
            )
    }

    sealed interface OversendtUtbetaling : Utbetaling {
        val simulering: Simulering
        val utbetalingsrequest: Utbetalingsrequest

        data class UtenKvittering(
            private val simulertUtbetaling: SimulertUtbetaling,
            override val simulering: Simulering,
            override val utbetalingsrequest: Utbetalingsrequest,
        ) : OversendtUtbetaling, Utbetaling by simulertUtbetaling {
            fun toKvittertUtbetaling(kvittering: Kvittering) =
                MedKvittering(
                    this,
                    kvittering = kvittering,
                )
        }

        data class MedKvittering(
            private val utenKvittering: UtenKvittering,
            val kvittering: Kvittering,
        ) : OversendtUtbetaling by utenKvittering {
            fun kvittertMedFeilEllerVarsel() =
                listOf(
                    Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
                    Kvittering.Utbetalingsstatus.FEIL,
                ).contains(kvittering.utbetalingsstatus)
        }
    }
}

/**
 * Returnerer utbetalingene sortert økende etter tidspunktet de er sendt til oppdrag. Filtrer bort de som er kvittert feil.
 * TODO jah: Ved initialisering e.l. gjør en faktisk verifikasjon på at ref-verdier på utbetalingslinjene har riktig rekkefølge
 */
internal fun List<Utbetaling>.hentOversendteUtbetalingerUtenFeil(): List<Utbetaling> =
    this.filter { it is Utbetaling.OversendtUtbetaling.UtenKvittering || it is Utbetaling.OversendtUtbetaling.MedKvittering && it.kvittering.erKvittertOk() }
        .sortedBy { it.opprettet.instant } // TODO potentially fix sorting

internal fun List<Utbetaling>.hentOversendteUtbetalingslinjerUtenFeil(): List<Utbetalingslinje> {
    return hentOversendteUtbetalingerUtenFeil().flatMap { it.utbetalingslinjer }
}

internal fun List<Utbetaling>.hentSisteOversendteUtbetalingslinjeUtenFeil(): Utbetalingslinje? {
    return hentOversendteUtbetalingerUtenFeil().lastOrNull()?.sisteUtbetalingslinje()
}

sealed class UtbetalingFeilet {
    object SimuleringHarBlittEndretSidenSaksbehandlerSimulerte : UtbetalingFeilet()
    object Protokollfeil : UtbetalingFeilet()
    data class KunneIkkeSimulere(val simuleringFeilet: SimuleringFeilet) : UtbetalingFeilet()
    object KontrollAvSimuleringFeilet : UtbetalingFeilet()
    object FantIkkeSak : UtbetalingFeilet()
}

fun List<Utbetaling>.tidslinje(
    clock: Clock,
    periode: Periode? = null,
): Either<IngenUtbetalinger, TidslinjeForUtbetalinger> {
    return flatMap { it.utbetalingslinjer }.tidslinje(
        clock = clock,
        periode = periode,
    )
}

@JvmName("utbetalingslinjeTidslinje")
fun List<Utbetalingslinje>.tidslinje(
    clock: Clock,
    periode: Periode? = null,
): Either<IngenUtbetalinger, TidslinjeForUtbetalinger> {
    return ifEmpty { return IngenUtbetalinger.left() }
        .let { utbetalingslinjer ->
            TidslinjeForUtbetalinger(
                periode = periode ?: Periode.create(
                    fraOgMed = minOf { it.fraOgMed },
                    tilOgMed = maxOf { it.tilOgMed },
                ),
                utbetalingslinjer = utbetalingslinjer,
                clock = clock,
            ).right()
        }
}

object IngenUtbetalinger

fun Sak.hentGjeldendeUtbetaling(
    forDato: LocalDate,
    clock: Clock,
): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
    return this.utbetalinger.hentGjeldendeUtbetaling(
        forDato = forDato,
        clock = clock,
    )
}

fun List<Utbetaling>.hentGjeldendeUtbetaling(
    forDato: LocalDate,
    clock: Clock,
): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
    return tidslinje(clock).fold(
        { FantIkkeGjeldendeUtbetaling.left() },
        { it.gjeldendeForDato(forDato)?.right() ?: FantIkkeGjeldendeUtbetaling.left() },
    )
}

object FantIkkeGjeldendeUtbetaling
