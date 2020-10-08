package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.Comparator

data class Utbetaling(
    val id: UUID30 = UUID30.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val simulering: Simulering? = null,
    val kvittering: Kvittering? = null,
    val oppdragsmelding: Oppdragsmelding? = null,
    val utbetalingslinjer: List<Utbetalingslinje>,
    val avstemmingId: UUID30? = null,
    val fnr: Fnr
) {
    fun sisteUtbetalingslinje() = utbetalingslinjer.lastOrNull()

    /**
     * Er oversendt OK til det eksterne oppdragssystemet (utbetalinger o.l.)
     */
    fun erOversendt() = oppdragsmelding?.erSendt() ?: false
    fun erKvittert() = kvittering != null
    fun erKvittertOk() = kvittering?.erKvittertOk() ?: false
    fun erKvittertFeil() = kvittering?.erKvittertOk() == false
    fun erStansutbetaling() = sisteUtbetalingslinje()?.let {
        // TODO jah: I en annen pull-request bør vi utvide en utbetaling til å være en sealed class med de forskjellig typene utbetaling.
        it.beløp == 0 // Stopputbetalinger vil ha beløp 0. Vi ønsker ikke å stoppe en stopputbetaling.
    } ?: false

    fun kanSlettes() = oppdragsmelding == null && kvittering == null

    object Opprettet : Comparator<Utbetaling> {
        override fun compare(o1: Utbetaling?, o2: Utbetaling?): Int {
            return o1!!.opprettet.toEpochMilli().compareTo(o2!!.opprettet.toEpochMilli())
        }
    }

    fun tidligsteDato() = utbetalingslinjer.minByOrNull { it.fraOgMed }!!.fraOgMed
    fun senesteDato() = utbetalingslinjer.maxByOrNull { it.tilOgMed }!!.tilOgMed
    fun bruttoBeløp() = utbetalingslinjer.sumBy { it.beløp }
}
