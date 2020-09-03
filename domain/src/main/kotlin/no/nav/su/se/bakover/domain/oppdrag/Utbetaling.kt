package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Utbetaling(
    val id: UUID30 = UUID30.randomUUID(),
    val opprettet: Instant = now(),
    val oppdragId: UUID30,
    val behandlingId: UUID,
    private var simulering: Simulering? = null,
    private var kvittering: Kvittering? = null,
    val utbetalingslinjer: List<Utbetalingslinje>,
) : PersistentDomainObject<UtbetalingPersistenceObserver>() {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun getSimulering(): Simulering? = simulering
    fun addSimulering(simulering: Simulering) {
        this.simulering = persistenceObserver.addSimulering(id, simulering)
    }

    fun sisteUtbetalingslinje() = utbetalingslinjer.last()

    fun førsteDag(): LocalDate = utbetalingslinjer.map { it.fom }.minOrNull()!!
    fun sisteDag(): LocalDate = utbetalingslinjer.map { it.tom }.maxOrNull()!!

    fun addKvittering(kvittering: Kvittering) {
        if(this.kvittering != null) {
            log.info("Kvittering allerede lagret.")
        } else {
            this.kvittering = persistenceObserver.addKvittering(id, kvittering)
        }
    }

    object Opprettet : Comparator<Utbetaling> {
        override fun compare(o1: Utbetaling?, o2: Utbetaling?): Int {
            return (o1!!.opprettet.toEpochMilli() - o2!!.opprettet.toEpochMilli()).toInt()
        }
    }
}

interface UtbetalingPersistenceObserver : PersistenceObserver {
    fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Simulering
    fun addKvittering(utbetalingId: UUID30, kvittering: Kvittering): Kvittering
}
