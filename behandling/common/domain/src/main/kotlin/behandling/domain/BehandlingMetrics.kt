package behandling.domain

// TODO jah: Ingen benytter disse metrikkene. Slett?
interface BehandlingMetrics {
    fun incrementUnderkjentCounter(handling: UnderkjentHandlinger)
    fun incrementInnvilgetCounter(handling: InnvilgetHandlinger)
    fun incrementAvslåttCounter(handling: AvslåttHandlinger)
    fun incrementTilAttesteringCounter(handling: TilAttesteringHandlinger)

    enum class InnvilgetHandlinger {
        PERSISTERT,
        JOURNALFØRT,
        LUKKET_OPPGAVE,
        DISTRIBUERT_BREV,
    }

    enum class AvslåttHandlinger {
        PERSISTERT,
        JOURNALFØRT,
        LUKKET_OPPGAVE,
        DISTRIBUERT_BREV,
    }

    enum class TilAttesteringHandlinger {
        PERSISTERT,
        OPPRETTET_OPPGAVE,
        LUKKET_OPPGAVE,
    }

    enum class UnderkjentHandlinger {
        PERSISTERT,
        OPPRETTET_OPPGAVE,
        LUKKET_OPPGAVE,
    }
}
