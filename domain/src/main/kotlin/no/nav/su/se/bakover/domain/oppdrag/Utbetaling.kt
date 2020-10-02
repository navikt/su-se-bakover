package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.slf4j.LoggerFactory
import java.util.Comparator

data class Utbetaling(
    val id: UUID30 = UUID30.randomUUID(),
    val opprettet: Tidspunkt = now(),
    private var simulering: Simulering? = null,
    private var kvittering: Kvittering? = null,
    private var oppdragsmelding: Oppdragsmelding? = null,
    val utbetalingslinjer: List<Utbetalingslinje>,
    private var avstemmingId: UUID30? = null,
    val fnr: Fnr
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun getKvittering() = kvittering

    fun getSimulering(): Simulering? = simulering

    fun getOppdragsmelding() = oppdragsmelding

    fun getAvstemmingId() = avstemmingId

    fun sisteUtbetalingslinje() = utbetalingslinjer.lastOrNull()

    /**
     * Er oversendt OK til det eksterne oppdragssystemet (utbetalinger o.l.)
     */
    fun erOversendt() = getOppdragsmelding()?.erSendt() ?: false
    fun erKvittert() = getKvittering() != null
    fun erKvittertOk() = getKvittering()?.erKvittertOk() ?: false
    fun erKvittertFeil() = getKvittering()?.erKvittertOk() == false
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
}
