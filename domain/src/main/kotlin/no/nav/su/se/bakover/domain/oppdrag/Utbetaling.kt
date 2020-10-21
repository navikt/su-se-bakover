package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

sealed class Utbetaling {
    abstract val id: UUID30
    abstract val opprettet: Tidspunkt
    abstract val fnr: Fnr
    abstract val utbetalingslinjer: List<Utbetalingslinje>
    abstract val type: UtbetalingsType
    abstract val oppdragId: UUID30
    abstract val behandler: NavIdentBruker
    abstract val avstemmingsnøkkel: Avstemmingsnøkkel

    fun sisteUtbetalingslinje() = utbetalingslinjer.lastOrNull()
    fun erFørstegangsUtbetaling() = utbetalingslinjer.any { it.forrigeUtbetalingslinjeId == null }

    fun tidligsteDato() = utbetalingslinjer.minByOrNull { it.fraOgMed }!!.fraOgMed
    fun senesteDato() = utbetalingslinjer.maxByOrNull { it.tilOgMed }!!.tilOgMed
    fun bruttoBeløp() = utbetalingslinjer.sumBy { it.beløp }

    data class UtbetalingForSimulering(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingsType,
        override val oppdragId: UUID30,
        override val behandler: NavIdentBruker,
        override val avstemmingsnøkkel: Avstemmingsnøkkel
    ) : Utbetaling() {
        fun toSimulertUtbetaling(simulering: Simulering) =
            SimulertUtbetaling(
                id = id,
                opprettet = opprettet,
                fnr = fnr,
                utbetalingslinjer = utbetalingslinjer,
                type = type,
                oppdragId = oppdragId,
                behandler = behandler,
                avstemmingsnøkkel = avstemmingsnøkkel,
                simulering = simulering
            )
    }

    data class SimulertUtbetaling(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingsType,
        override val oppdragId: UUID30,
        override val behandler: NavIdentBruker,
        override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
        val simulering: Simulering,
    ) : Utbetaling() {
        fun toOversendtUtbetaling(oppdragsmelding: Utbetalingsrequest) =
            OversendtUtbetaling.UtenKvittering(
                id = id,
                opprettet = opprettet,
                fnr = fnr,
                utbetalingslinjer = utbetalingslinjer,
                type = type,
                oppdragId = oppdragId,
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
            override val opprettet: Tidspunkt = now(),
            override val fnr: Fnr,
            override val utbetalingslinjer: List<Utbetalingslinje>,
            override val type: UtbetalingsType,
            override val oppdragId: UUID30,
            override val behandler: NavIdentBruker,
            override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
            override val simulering: Simulering,
            override val utbetalingsrequest: Utbetalingsrequest
        ) : OversendtUtbetaling() {
            fun toKvittertUtbetaling(kvittering: Kvittering) =
                MedKvittering(
                    id = id,
                    opprettet = opprettet,
                    fnr = fnr,
                    utbetalingslinjer = utbetalingslinjer,
                    type = type,
                    oppdragId = oppdragId,
                    behandler = behandler,
                    avstemmingsnøkkel = avstemmingsnøkkel,
                    simulering = simulering,
                    utbetalingsrequest = utbetalingsrequest,
                    kvittering = kvittering
                )
        }

        data class MedKvittering(
            override val id: UUID30 = UUID30.randomUUID(),
            override val opprettet: Tidspunkt = now(),
            override val fnr: Fnr,
            override val utbetalingslinjer: List<Utbetalingslinje>,
            override val type: UtbetalingsType,
            override val oppdragId: UUID30,
            override val behandler: NavIdentBruker,
            override val avstemmingsnøkkel: Avstemmingsnøkkel = Avstemmingsnøkkel(opprettet),
            override val simulering: Simulering,
            override val utbetalingsrequest: Utbetalingsrequest,
            val kvittering: Kvittering
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
        GJENOPPTA
    }
}
