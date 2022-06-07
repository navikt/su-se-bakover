package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.rightIfNotNull
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
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
    val type: UtbetalingsType
    val behandler: NavIdentBruker
    val avstemmingsnøkkel: Avstemmingsnøkkel
    val sakstype: Sakstype

    fun sisteUtbetalingslinje() = utbetalingslinjer.last()
    fun erFørstegangsUtbetaling() = utbetalingslinjer.any { it.forrigeUtbetalingslinjeId == null }

    fun tidligsteDato() = utbetalingslinjer.minByOrNull { it.fraOgMed }!!.fraOgMed
    fun senesteDato() = utbetalingslinjer.maxByOrNull { it.tilOgMed }!!.tilOgMed
    fun bruttoBeløp() = utbetalingslinjer.sumOf { it.beløp }

    data class UtbetalingForSimulering(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        override val type: UtbetalingsType,
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

    enum class UtbetalingsType {
        NY,
        STANS,
        GJENOPPTA,
        OPPHØR,
    }

    companion object {
        /**
         * Returnerer utbetalingene sortert økende etter tidspunktet de er sendt til oppdrag. Filtrer bort de som er kvittert feil.
         * TODO jah: Ved initialisering e.l. gjør en faktisk verifikasjon på at ref-verdier på utbetalingslinjene har riktig rekkefølge
         */
        fun List<Utbetaling>.hentOversendteUtbetalingerUtenFeil(): List<Utbetaling> =
            this.filter { it is OversendtUtbetaling.UtenKvittering || it is OversendtUtbetaling.MedKvittering && it.kvittering.erKvittertOk() }
                .sortedBy { it.opprettet.instant } // TODO potentially fix sorting
    }
}

sealed class UtbetalingFeilet {
    object SimuleringHarBlittEndretSidenSaksbehandlerSimulerte : UtbetalingFeilet()
    object Protokollfeil : UtbetalingFeilet()
    data class KunneIkkeSimulere(val simuleringFeilet: SimuleringFeilet) : UtbetalingFeilet()
    object KontrollAvSimuleringFeilet : UtbetalingFeilet()
    object FantIkkeSak : UtbetalingFeilet()
}

fun Sak.hentGjeldendeUtbetaling(
    forDato: LocalDate,
    clock: Clock,
): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
    return this.utbetalinger.hentGjeldendeUtbetaling(forDato, clock)
}

fun List<Utbetaling>.hentGjeldendeUtbetaling(
    forDato: LocalDate,
    clock: Clock,
): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
    return this
        .flatMap { it.utbetalingslinjer }
        .ifNotEmpty {
            TidslinjeForUtbetalinger(
                periode = this.map { it.periode }.minAndMaxOf(),
                utbetalingslinjer = this,
                clock = clock,
            ).gjeldendeForDato(forDato)
        }.rightIfNotNull { FantIkkeGjeldendeUtbetaling }
}

object FantIkkeGjeldendeUtbetaling
