package no.nav.su.se.bakover.domain.regulering

import java.util.UUID

/**
 * En samling av alle reguleringer for en sak.
 */
data class Reguleringer(
    val sakId: UUID,
    val behandlinger: List<Regulering>,
) : List<Regulering> by behandlinger {
    init {
        this.map { it.id }.let {
            require(it.distinct() == it) {
                "Regulering for sak $sakId kan ikke inneholde duplikate IDer, men var: $it"
            }
        }
    }

    fun hent(id: ReguleringId): Regulering? {
        val behandling = behandlinger.filter { it.id == id }

        return when {
            behandling.isEmpty() -> null
            behandling.size == 1 -> behandling[0]
            // Dette er også garantert av init
            else -> throw IllegalStateException("Mer enn 1 regulering for unik id: $id")
        }
    }

    fun harÅpen() = this.behandlinger.any { it.erÅpen() }

    /**
     * @throws IllegalStateException hvis regulering med samme id finnes fra før.
     */
    fun nyRegulering(regulering: Regulering): Reguleringer {
        if (this.any { it.id == regulering.id }) {
            throw java.lang.IllegalStateException(
                "Regulering med id ${regulering.id} finnes fra før for sakId $sakId.",
                RuntimeException("Trigger stacktrace for enklere debug."),
            )
        }
        return this.copy(behandlinger = this.behandlinger + regulering)
    }

    /**
     * @throws IllegalStateException hvis regulering med samme id ikke finnes fra før.
     */
    fun oppdaterRegulering(regulering: Regulering): Reguleringer {
        if (behandlinger.none { it.id == regulering.id }) {
            throw java.lang.IllegalStateException(
                "Regulering med id ${regulering.id} finnes ikke fra før. for sakId $sakId.",
                RuntimeException("Trigger stacktrace for enklere debug."),
            )
        }
        return this.copy(
            behandlinger = this.behandlinger.map {
                if (it.id == regulering.id) regulering else it
            },
        )
    }

    companion object {
        fun empty(sakId: UUID) = Reguleringer(sakId, listOf())
    }
}
