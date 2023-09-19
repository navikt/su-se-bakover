package no.nav.su.se.bakover.oppgave.domain

import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId

data class Oppgave(
    val id: OppgaveId,
    val versjon: Int,
    val status: Oppgavestatus,
) {
    fun erÅpen(): Boolean {
        return status.erÅpen()
    }

    enum class Oppgavestatus {
        Opprettet,
        Åpnet,
        UnderBehandling,
        Ferdigstilt,
        Feilregistert,
        ;

        fun erÅpen(): Boolean {
            return when (this) {
                Opprettet, Åpnet, UnderBehandling -> true
                Ferdigstilt, Feilregistert -> false
            }
        }
    }
}
