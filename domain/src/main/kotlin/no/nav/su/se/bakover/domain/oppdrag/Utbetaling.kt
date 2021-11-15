package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.util.UUID

sealed class Utbetaling {
    abstract val id: UUID30
    abstract val opprettet: Tidspunkt
    abstract val sakId: UUID
    abstract val saksnummer: Saksnummer
    abstract val fnr: Fnr
    abstract val utbetalingslinjer: NonEmptyList<Utbetalingslinje>
    abstract val type: UtbetalingsType
    abstract val behandler: NavIdentBruker
    abstract val avstemmingsnøkkel: Avstemmingsnøkkel

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
    ) : Utbetaling() {
        fun toSimulertUtbetaling(simulering: Simulering) =
            SimulertUtbetaling(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = utbetalingslinjer,
                type = type,
                behandler = behandler,
                avstemmingsnøkkel = avstemmingsnøkkel,
                simulering = simulering
            )
    }

    data class SimulertUtbetaling(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
        override val type: UtbetalingsType,
        override val behandler: NavIdentBruker,
        override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
        val simulering: Simulering,
    ) : Utbetaling() {
        fun toOversendtUtbetaling(oppdragsmelding: Utbetalingsrequest) =
            OversendtUtbetaling.UtenKvittering(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                utbetalingslinjer = utbetalingslinjer,
                type = type,
                behandler = behandler,
                avstemmingsnøkkel = avstemmingsnøkkel,
                simulering = simulering,
                utbetalingsrequest = oppdragsmelding
            )
    }

    sealed class OversendtUtbetaling : Utbetaling() {
        abstract val simulering: Simulering
        abstract val utbetalingsrequest: Utbetalingsrequest

        data class UtenKvittering(
            override val id: UUID30 = UUID30.randomUUID(),
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
            override val type: UtbetalingsType,
            override val behandler: NavIdentBruker,
            override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
            override val simulering: Simulering,
            override val utbetalingsrequest: Utbetalingsrequest,
        ) : OversendtUtbetaling() {
            fun toKvittertUtbetaling(kvittering: Kvittering) =
                MedKvittering(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    utbetalingslinjer = utbetalingslinjer,
                    type = type,
                    behandler = behandler,
                    avstemmingsnøkkel = avstemmingsnøkkel,
                    simulering = simulering,
                    utbetalingsrequest = utbetalingsrequest,
                    kvittering = kvittering
                )
        }

        data class MedKvittering(
            override val id: UUID30 = UUID30.randomUUID(),
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
            override val type: UtbetalingsType,
            override val behandler: NavIdentBruker,
            override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
            override val simulering: Simulering,
            override val utbetalingsrequest: Utbetalingsrequest,
            val kvittering: Kvittering,
        ) : OversendtUtbetaling() {
            fun kvittertMedFeilEllerVarsel() =
                listOf(
                    Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
                    Kvittering.Utbetalingsstatus.FEIL
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
