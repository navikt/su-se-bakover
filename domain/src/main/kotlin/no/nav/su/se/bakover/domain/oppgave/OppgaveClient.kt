package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either

interface OppgaveClient {
    fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, Long>
}

data class KunneIkkeOppretteOppgave(
    val statuskode: Int,
    val melding: String
)
