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
    abstract val simulering: Simulering?
    abstract val kvittering: Kvittering?
    abstract val oppdragsmelding: Oppdragsmelding?
    abstract val utbetalingslinjer: List<Utbetalingslinje>
    abstract val avstemmingId: UUID30?
    abstract val fnr: Fnr
    abstract val type: UtbetalingType

    fun sisteUtbetalingslinje() = utbetalingslinjer.lastOrNull()

    /**
     * Er oversendt OK til det eksterne oppdragssystemet (utbetalinger o.l.)
     */
    fun erOversendt() = oppdragsmelding?.erSendt() ?: false
    fun erKvittert() = kvittering != null
    fun erKvittertOk() = kvittering?.erKvittertOk() ?: false
    fun erKvittertFeil() = kvittering?.erKvittertOk() == false

    fun kanSlettes() = oppdragsmelding == null && kvittering == null

    object Opprettet : Comparator<Utbetaling> {
        override fun compare(o1: Utbetaling?, o2: Utbetaling?): Int {
            return o1!!.opprettet.toEpochMilli().compareTo(o2!!.opprettet.toEpochMilli())
        }
    }

    fun tidligsteDato() = utbetalingslinjer.minByOrNull { it.fraOgMed }!!.fraOgMed
    fun senesteDato() = utbetalingslinjer.maxByOrNull { it.tilOgMed }!!.tilOgMed
    fun bruttoBeløp() = utbetalingslinjer.sumBy { it.beløp }

    data class Ny(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val simulering: Simulering? = null,
        override val kvittering: Kvittering? = null,
        override val oppdragsmelding: Oppdragsmelding? = null,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val avstemmingId: UUID30? = null,
        override val fnr: Fnr,
        override val type: UtbetalingType = UtbetalingType.NY
    ) : Utbetaling()

    data class Stans(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val simulering: Simulering? = null,
        override val kvittering: Kvittering? = null,
        override val oppdragsmelding: Oppdragsmelding? = null,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val avstemmingId: UUID30? = null,
        override val fnr: Fnr,
        override val type: UtbetalingType = UtbetalingType.STANS
    ) : Utbetaling() {
        init {
            require(utbetalingslinjer.all { it.beløp == 0 }) { "Stans kan bare inneholde utbetalingslinjer med beløp = 0!" }
        }
    }

    data class Gjenoppta(
        override val id: UUID30 = UUID30.randomUUID(),
        override val opprettet: Tidspunkt = now(),
        override val simulering: Simulering? = null,
        override val kvittering: Kvittering? = null,
        override val oppdragsmelding: Oppdragsmelding? = null,
        override val utbetalingslinjer: List<Utbetalingslinje>,
        override val avstemmingId: UUID30? = null,
        override val fnr: Fnr,
        override val type: UtbetalingType = UtbetalingType.GJENOPPTA
    ) : Utbetaling()

    enum class UtbetalingType {
        NY,
        STANS,
        GJENOPPTA
    }
}
