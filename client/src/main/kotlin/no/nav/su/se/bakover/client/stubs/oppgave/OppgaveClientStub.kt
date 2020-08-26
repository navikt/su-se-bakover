package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import kotlin.random.Random

object OppgaveClientStub : OppgaveClient {
    override fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, Long> =
        Random.nextLong().right()
}
