package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.Either
import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.and
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.Simulering
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
        utbetalingslinjer.kontrollerUtbetalingslinjer()
    }

    fun tidslinje(): TidslinjeForUtbetalinger {
        return TidslinjeForUtbetalinger.fra(this)
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
        init {
            kontrollerUtbetalingslinjer()
        }
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
 * Brukes internt av [Utbetaling], men også av [TidslinjeForUtbetalinger] (siden den lager en tidslinje av utbetalingslinjer før den har laget en komplett utbetaling.)
 */
fun List<Utbetalingslinje>.kontrollerUtbetalingslinjer() {
    this.sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse()
    this.sjekkSortering()
    this.sjekkRekkefølge()
    this.sjekkSammeForrigeUtbetalingsId()
    this.sjekkSammeUtbetalingsId()
    this.sjekkForrigeForNye()
}

sealed class UtbetalingFeilet {
    data class SimuleringHarBlittEndretSidenSaksbehandlerSimulerte(val feil: KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet) :
        UtbetalingFeilet()

    data object Protokollfeil : UtbetalingFeilet()

    data class KunneIkkeSimulere(val simuleringFeilet: SimulerUtbetalingFeilet) : UtbetalingFeilet()
    data object FantIkkeSak : UtbetalingFeilet()
}

data object IngenUtbetalinger

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
