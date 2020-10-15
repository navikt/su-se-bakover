package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.Comparator

sealed class Utbetaling {
    abstract val id: UUID30
    abstract val opprettet: Tidspunkt
    abstract val fnr: Fnr
    abstract val utbetalingslinjer: List<Utbetalingslinje>
    abstract val type: UtbetalingType

    fun sisteUtbetalingslinje() = utbetalingslinjer.lastOrNull()

    object Opprettet : Comparator<Utbetaling> {
        override fun compare(o1: Utbetaling?, o2: Utbetaling?): Int {
            return o1!!.opprettet.toEpochMilli().compareTo(o2!!.opprettet.toEpochMilli())
        }
    }

    fun tidligsteDato() = utbetalingslinjer.minByOrNull { it.fraOgMed }!!.fraOgMed
    fun senesteDato() = utbetalingslinjer.maxByOrNull { it.tilOgMed }!!.tilOgMed
    fun bruttoBeløp() = utbetalingslinjer.sumBy { it.beløp }

    data class UtbetalingForSimulering(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingType,
    ) : Utbetaling() {
        fun toSimulertUtbetaling(simulering: Simulering) =
            SimulertUtbetaling(id, opprettet, fnr, utbetalingslinjer, type, simulering)
    }

    data class SimulertUtbetaling(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingType,
        val simulering: Simulering
    ) : Utbetaling() {
        fun toOversendtUtbetaling(oppdragsmelding: Oppdragsmelding) =
            OversendtUtbetaling(id, opprettet, fnr, utbetalingslinjer, type, simulering, oppdragsmelding)
    }

    data class OversendtUtbetaling(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingType,
        val simulering: Simulering,
        val oppdragsmelding: Oppdragsmelding
    ) : Utbetaling() {
        fun toKvittertUtbetaling(kvittering: Kvittering) =
            KvittertUtbetaling(id, opprettet, fnr, utbetalingslinjer, type, simulering, oppdragsmelding, kvittering)
    }

    data class KvittertUtbetaling(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingType,
        val simulering: Simulering,
        val oppdragsmelding: Oppdragsmelding,
        val kvittering: Kvittering
    ) : Utbetaling()

    data class AvstemtUtbetaling(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val fnr: Fnr,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val type: UtbetalingType,
        val simulering: Simulering,
        val oppdragsmelding: Oppdragsmelding,
        val kvittering: Kvittering,
        val avstemmingId: UUID30
    ) : Utbetaling()

    enum class UtbetalingType {
        NY,
        STANS,
        GJENOPPTA
    }
}
