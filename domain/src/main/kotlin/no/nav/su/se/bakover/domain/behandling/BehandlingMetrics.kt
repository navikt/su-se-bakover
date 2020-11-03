package no.nav.su.se.bakover.domain.behandling

interface BehandlingMetrics {
    fun behandlingsstatusChanged(old: Behandling.BehandlingsStatus, new: Behandling.BehandlingsStatus)
    fun incrementUnderkjentCounter()
    fun incrementInnvilgetCounter(handling: InnvilgetHandlinger)
    fun incrementAvslåttCounter(handling: AvslåttHandlinger)
    fun incrementTilAttesteringCounter(handling: TilAttesteringHandlinger)

    enum class InnvilgetHandlinger {
        PERSISTERT,
        JOURNALFØRT,
        OPPGAVE,
        DISTRIBUERT_BREV
    }

    enum class AvslåttHandlinger {
        PERSISTERT,
        JOURNALFØRT,
        LUKKET_OPPGAVE,
        DISTRIBUERT_BREV
    }

    enum class TilAttesteringHandlinger {
        PERSISTERT,
        OPPGAVE,
    }
}
