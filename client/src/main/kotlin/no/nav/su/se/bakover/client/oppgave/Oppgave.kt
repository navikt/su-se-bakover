package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError

interface Oppgave {
    fun opprettOppgave(config: OppgaveConfig): Either<ClientError, Long>
}
