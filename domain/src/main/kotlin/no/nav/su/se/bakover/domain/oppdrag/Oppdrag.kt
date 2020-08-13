package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import org.threeten.extra.Interval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class Oppdrag(
    id: UUID = UUID.randomUUID(), // oppdragsid/kankskje avstemmingsnøkkel?
    opprettet: Instant = now(),
    private val sakId: UUID, // fagsystemId,
    private val behandlingId: UUID,
    private val endringskode: Endringskode,
    private var simulering: Simulering? = null,
    private val oppdragslinjer: List<Oppdragslinje>
) : PersistentDomainObject<OppdragPersistenceObserver>(id, opprettet), DtoConvertable<OppdragDto> {
    enum class Endringskode {
        NY, ENDR
    }

    override fun toDto(): OppdragDto {
        return OppdragDto(id, opprettet, sakId, behandlingId, endringskode, oppdragslinjer)
    }

    fun addSimulering(simulering: Simulering) {
        this.simulering = persistenceObserver.addSimulering(id, simulering)
    }

    override fun equals(other: Any?) = other is Oppdrag && other.sakId == sakId && other.behandlingId == behandlingId && other.oppdragslinjer == oppdragslinjer && other.endringskode == endringskode

    fun overlapper(fom: LocalDate, tom: LocalDate) = oppdragslinjeSpan().overlaps(Interval.of(fom.toInterval(), tom.toInterval()))

    fun oppdragslinjeSpan(): Interval = Interval.of(
        oppdragslinjer.map { it.fom }.minBy { it }!!.toInterval(),
        oppdragslinjer.map { it.tom }.maxBy { it }!!.plusDays(1).toInterval()
    )
}

fun LocalDate.toInterval() = this.atStartOfDay(ZoneId.systemDefault()).toInstant()

interface OppdragPersistenceObserver : PersistenceObserver {
    fun addSimulering(oppdragsId: UUID, simulering: Simulering): Simulering
}

data class OppdragDto(
    val id: UUID,
    val opprettet: Instant,
    val sakId: UUID,
    val behandlingId: UUID,
    val endringskode: Oppdrag.Endringskode,
    val oppdragslinjer: List<Oppdragslinje>
)
