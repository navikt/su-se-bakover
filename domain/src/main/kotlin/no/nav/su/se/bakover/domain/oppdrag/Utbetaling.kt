package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering

sealed class Utbetaling {
    abstract val id: UUID30
    abstract val opprettet: Tidspunkt
    abstract val fnr: Fnr
    abstract val utbetalingslinjer: List<Utbetalingslinje>
    abstract val type: UtbetalingsType
    abstract val oppdragId: UUID30
    abstract val behandler: NavIdentBruker

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
        override val behandler: NavIdentBruker
    ) : Utbetaling() {
        fun toSimulertUtbetaling(simulering: Simulering) =
            SimulertUtbetaling(id, opprettet, fnr, utbetalingslinjer, type, oppdragId, behandler, simulering)
    }

    data class SimulertUtbetaling(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingsType,
        override val oppdragId: UUID30,
        override val behandler: NavIdentBruker,
        val simulering: Simulering,
    ) : Utbetaling() {
        fun toOversendtUtbetaling(oppdragsmelding: Oppdragsmelding) =
            OversendtUtbetaling(id, opprettet, fnr, utbetalingslinjer, type, oppdragId, behandler, simulering, oppdragsmelding)
    }

    data class OversendtUtbetaling(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingsType,
        override val oppdragId: UUID30,
        override val behandler: NavIdentBruker,
        val simulering: Simulering,
        val oppdragsmelding: Oppdragsmelding
    ) : Utbetaling() {
        fun toKvittertUtbetaling(kvittering: Kvittering) =
            KvittertUtbetaling(id, opprettet, fnr, utbetalingslinjer, type, oppdragId, behandler, simulering, oppdragsmelding, kvittering)
    }

    data class KvittertUtbetaling(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingsType,
        override val oppdragId: UUID30,
        override val behandler: NavIdentBruker,
        val simulering: Simulering,
        val oppdragsmelding: Oppdragsmelding,
        val kvittering: Kvittering
    ) : Utbetaling() {
        fun kvittertMedFeilEllerVarsel() =
            listOf(
                Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
                Kvittering.Utbetalingsstatus.FEIL
            ).contains(kvittering.utbetalingsstatus)
    }

    data class AvstemtUtbetaling(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingsType,
        override val oppdragId: UUID30,
        override val behandler: NavIdentBruker,
        val simulering: Simulering,
        val oppdragsmelding: Oppdragsmelding,
        val kvittering: Kvittering,
        val avstemmingId: UUID30
    ) : Utbetaling()

    enum class UtbetalingsType {
        NY,
        STANS,
        GJENOPPTA
    }
}
