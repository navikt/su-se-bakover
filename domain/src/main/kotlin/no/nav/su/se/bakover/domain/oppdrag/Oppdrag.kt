package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.util.UUID

data class Oppdrag(
    override val id: UUID = UUID.randomUUID(), // oppdragsid/kankskje avstemmingsnøkkel?
    override val opprettet: Instant = now(),
    val sakId: UUID, // fagsystemId,
    val behandlingId: UUID,
    val endringskode: Endringskode,
    private var simulering: Simulering? = null,
    private val fagområde: Fagområde = Fagområde.FAGOMR,
    private val utbetalingsfrekvens: Utbetalingsfrekvens = Utbetalingsfrekvens.MND,
    private val fagsystem: Fagsystem = Fagsystem.FAGSYSTEM,
    private val oppdragGjelder: String,
    val oppdragslinjer: List<Oppdragslinje>

) : PersistentDomainObject<OppdragPersistenceObserver>(), DtoConvertable<OppdragDto> {

    override fun toDto(): OppdragDto {
        return OppdragDto(
            id,
            opprettet,
            sakId,
            behandlingId = behandlingId,
            endringskode = endringskode,
            fagområde = fagområde,
            utbetalingsfrekvens = utbetalingsfrekvens,
            fagsystem = fagsystem,
            oppdragGjelder = oppdragGjelder,
            oppdragslinjer = oppdragslinjer
        )
    }

    fun addSimulering(simulering: Simulering) {
        this.simulering = persistenceObserver.addSimulering(id, simulering)
    }

    fun sisteOppdragslinje() = oppdragslinjer.last()

    enum class Endringskode {
        NY, ENDR
    }

    enum class Fagsystem {
        FAGSYSTEM // TODO decide with os
    }

    enum class Utbetalingsfrekvens {
        MND
    }

    enum class Fagområde {
        FAGOMR // TODO decide with OS
    }
}

interface OppdragPersistenceObserver : PersistenceObserver {
    fun addSimulering(oppdragsId: UUID, simulering: Simulering): Simulering
}

data class OppdragDto(
    val id: UUID,
    val opprettet: Instant,
    val sakId: UUID,
    val behandlingId: UUID,
    val endringskode: Oppdrag.Endringskode,
    val fagområde: Oppdrag.Fagområde,
    val utbetalingsfrekvens: Oppdrag.Utbetalingsfrekvens,
    val fagsystem: Oppdrag.Fagsystem,
    val oppdragGjelder: String,
    val oppdragslinjer: List<Oppdragslinje>
)
