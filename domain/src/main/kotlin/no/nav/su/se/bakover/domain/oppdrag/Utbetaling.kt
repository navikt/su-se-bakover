package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.and
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import java.time.LocalDate
import java.util.UUID

sealed interface Utbetaling : Comparable<Utbetaling> {
    val id: UUID30
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
    val utbetalingslinjer: NonEmptyList<Utbetalingslinje>
    val behandler: NavIdentBruker
    val avstemmingsnøkkel: Avstemmingsnøkkel
    val sakstype: Sakstype

    override fun compareTo(other: Utbetaling): Int {
        return this.opprettet.instant.compareTo(other.opprettet.instant)
    }

    fun sisteUtbetalingslinje() = utbetalingslinjer.last()

    fun tidligsteDato() = utbetalingslinjer.minOf { it.periode.fraOgMed }
    fun senesteDato() = utbetalingslinjer.maxOf { it.periode.tilOgMed }
    fun bruttoBeløp() = utbetalingslinjer.sumOf { it.beløp }

    /**
     * Vi tillater kun stans som en midlertidig operasjon i nåtid. Ved stans vil vi sende med en ønsket
     * dato for stans, for deretter å stanse den siste utbetalte linjen fra denne datoen - følgelig skal en stans
     * inneholde nøyaktig 1 utbetalingslinje av typen stans. [Utbetalingslinje.Endring.virkningsperiode] for en stans
     * skal alltid gjelde fra angitt stansdato til og med seneste til og med dato for sakens utbetalinger.
     *
     * @see Utbetalingsstrategi.Stans
     * @see no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
     */
    fun erStans() = utbetalingslinjer.all { it is Utbetalingslinje.Endring.Stans }
        .and { utbetalingslinjer.count() == 1 }

    /**
     * Vi tillater kun å reaktivere utbetalinger som har blitt stanset ved hjelp av en utbetaling av typen [erStans].
     * Forholdet mellom en stans og en reaktivering er 1-1 i den forstand at en reaktivering ikke kan gjennomføres med
     * mindre den siste oversendte utbetalingen til OS er en [erStans].
     *
     * @see Utbetalingsstrategi.Gjenoppta
     * @see no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
     */
    fun erReaktivering() = utbetalingslinjer.all { it is Utbetalingslinje.Endring.Reaktivering }
        .and { utbetalingslinjer.count() == 1 }

    fun kontrollerUtbetalingslinjer() {
        utbetalingslinjer.sjekkAlleNyeLinjerHarForskjelligForrigeReferanse()
        utbetalingslinjer.sjekkSortering()
    }

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
        init {
            kontrollerUtbetalingslinjer()
        }

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
            init {
                kontrollerUtbetalingslinjer()
            }

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
            init {
                kontrollerUtbetalingslinjer()
            }

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
internal fun List<Utbetaling>.hentOversendteUtbetalingerUtenFeil(): List<Utbetaling> {
    return this.filter {
        when (it) {
            is Utbetaling.OversendtUtbetaling.MedKvittering -> it.kvittering.erKvittertOk()
            is Utbetaling.OversendtUtbetaling.UtenKvittering -> true
            is Utbetaling.SimulertUtbetaling,
            is Utbetaling.UtbetalingForSimulering,
            -> false
        }
    }.sorted()
}

internal fun List<Utbetaling>.hentOversendteUtbetalingslinjerUtenFeil(): List<Utbetalingslinje> {
    return hentOversendteUtbetalingerUtenFeil().flatMap { it.utbetalingslinjer }
}

internal fun List<Utbetaling>.hentSisteOversendteUtbetalingslinjeUtenFeil(): Utbetalingslinje? {
    return hentOversendteUtbetalingerUtenFeil().lastOrNull()?.sisteUtbetalingslinje()
}

internal fun List<Utbetaling>.harUtbetalingerEtter(date: LocalDate): Boolean {
    return hentOversendteUtbetalingslinjerUtenFeil().let { utbetalingslinjer ->
        if (utbetalingslinjer.isNotEmpty()) {
            utbetalingslinjer.maxOf { it.periode.tilOgMed } > date
        } else {
            false
        }
    }
}

sealed class UtbetalingFeilet {
    data class SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(val feil: KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet) :
        UtbetalingFeilet()

    object Protokollfeil : UtbetalingFeilet() {
        override fun toString() = this::class.simpleName!!
    }

    data class KunneIkkeSimulere(val simuleringFeilet: SimulerUtbetalingFeilet) : UtbetalingFeilet()
    object FantIkkeSak : UtbetalingFeilet() {
        override fun toString() = this::class.simpleName!!
    }
}

fun List<Utbetaling>.tidslinje(): Either<IngenUtbetalinger, TidslinjeForUtbetalinger> {
    return flatMap { it.utbetalingslinjer }.tidslinje()
}

@JvmName("utbetalingslinjeTidslinje")
fun List<Utbetalingslinje>.tidslinje(): Either<IngenUtbetalinger, TidslinjeForUtbetalinger> {
    return ifEmpty { return IngenUtbetalinger.left() }
        .let { utbetalingslinjer ->
            TidslinjeForUtbetalinger(utbetalingslinjer = utbetalingslinjer.toNonEmptyList()).right()
        }
}

object IngenUtbetalinger

fun Sak.hentGjeldendeUtbetaling(
    forDato: LocalDate,
): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
    return this.utbetalinger.hentGjeldendeUtbetaling(forDato = forDato)
}

fun List<Utbetaling>.hentGjeldendeUtbetaling(forDato: LocalDate): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
    return tidslinje().fold(
        { FantIkkeGjeldendeUtbetaling.left() },
        { it.gjeldendeForDato(forDato)?.right() ?: FantIkkeGjeldendeUtbetaling.left() },
    )
}

object FantIkkeGjeldendeUtbetaling

/**
 * @param utbetaling en simulert utbetaling med generert XML for publisering på kø mot oppdragssystemet (OS).
 * @param callback funksjon som publiserer generert XML for utbetalingen på kø mot OS
 */
data class UtbetalingKlargjortForOversendelse<T>(
    val utbetaling: Utbetaling.OversendtUtbetaling,
    val callback: (utbetalingsrequest: Utbetalingsrequest) -> Either<T, Utbetalingsrequest>,
) {
    /**
     * Publiserer utbetalingen på kø mot oppdrag.
     */
    fun sendUtbetaling(): Either<T, Utbetalingsrequest> {
        return callback(utbetaling.utbetalingsrequest)
    }
}
