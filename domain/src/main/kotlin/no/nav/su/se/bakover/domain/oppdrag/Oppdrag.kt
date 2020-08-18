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
    val endringskode: Endringskode,
    private var simulering: Simulering? = null,
    val utbetalingsfrekvens: Utbetalingsfrekvens = Utbetalingsfrekvens.MND,
    val oppdragGjelder: String,
    val oppdragslinjer: List<Oppdragslinje>,
    val saksbehandler: String,
    private var attestant: String? = null
) : PersistentDomainObject<OppdragPersistenceObserver>() {

    fun getAttestant() = attestant

    fun addSimulering(simulering: Simulering) {
        this.simulering = persistenceObserver.addSimulering(id, simulering)
    }

    fun sisteOppdragslinje() = oppdragslinjer.last()

    fun førsteDag(): LocalDate = oppdragslinjer.map { it.fom }.min()!!
    fun sisteDag(): LocalDate = oppdragslinjer.map { it.tom }.max()!!

    enum class Endringskode {
        NY, ENDR
    }

    enum class Utbetalingsfrekvens {
        MND
    }
}

interface OppdragPersistenceObserver : PersistenceObserver {
    fun addSimulering(oppdragsId: UUID, simulering: Simulering): Simulering
}
