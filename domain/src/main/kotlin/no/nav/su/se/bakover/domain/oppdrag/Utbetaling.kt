package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
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
) : PersistentDomainObject<UtbetalingPersistenceObserver>() {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun getKvittering() = kvittering

    fun getSimulering(): Simulering? = simulering

    fun addSimulering(simulering: Simulering) {
        this.simulering = simulering.also {
            persistenceObserver.addSimulering(id, simulering)
        }
    }

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

    fun addKvittering(kvittering: Kvittering) {
        if (this.kvittering != null) {
            log.info("Kvittering allerede lagret.")
        } else {
            this.kvittering = persistenceObserver.addKvittering(id, kvittering)
        }
    }

    fun addOppdragsmelding(oppdragsmelding: Oppdragsmelding) {
        this.oppdragsmelding = persistenceObserver.addOppdragsmelding(id, oppdragsmelding)
        when (this.oppdragsmelding!!.erSendt()) {
            true -> log.info("Oppdragsmelding for utbetaling: $id oversendt oppdrag")
            else -> log.warn("Oversendelse av oppdragsmelding for utbetaling: $id feilet")
        }
    }

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

interface UtbetalingPersistenceObserver : PersistenceObserver {
    fun addSimulering(utbetalingId: UUID30, simulering: Simulering)
    fun addKvittering(utbetalingId: UUID30, kvittering: Kvittering): Kvittering
    fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Oppdragsmelding
}
