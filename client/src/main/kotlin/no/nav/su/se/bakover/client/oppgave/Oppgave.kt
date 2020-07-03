package no.nav.su.se.bakover.client.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.client.ClientError

interface Oppgave {
    fun opprettOppgave(journalpostId: String, sakId: Long, aktørId: String): Either<ClientError, Long>
}
