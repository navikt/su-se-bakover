package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Oppdrag(
    override val id: UUID = UUID.randomUUID(), // Tror vi kan bruke denne som avstemmingsnøkkel.
    override val opprettet: Instant = now(),
    val sakId: UUID,
    val behandlingId: UUID,
    private var simulering: Simulering? = null,
    val oppdragslinjer: List<Oppdragslinje>,
) : PersistentDomainObject<OppdragPersistenceObserver>() {

    fun getSimulering(): Simulering? = simulering
    fun addSimulering(simulering: Simulering) {
        this.simulering = persistenceObserver.addSimulering(id, simulering)
    }

    fun sisteOppdragslinje() = oppdragslinjer.last()

    fun førsteDag(): LocalDate = oppdragslinjer.map { it.fom }.minOrNull()!!
    fun sisteDag(): LocalDate = oppdragslinjer.map { it.tom }.maxOrNull()!!

    object Opprettet : Comparator<Oppdrag> {
        override fun compare(o1: Oppdrag?, o2: Oppdrag?): Int {
            return (o1!!.opprettet.toEpochMilli() - o2!!.opprettet.toEpochMilli()).toInt()
        }
    }
}

interface OppdragPersistenceObserver : PersistenceObserver {
    fun addSimulering(oppdragsId: UUID, simulering: Simulering): Simulering
}
