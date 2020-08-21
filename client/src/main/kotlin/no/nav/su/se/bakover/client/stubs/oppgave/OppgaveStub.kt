package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.oppgave.Oppgave
import no.nav.su.se.bakover.client.oppgave.OppgaveConfig
import kotlin.random.Random

object OppgaveStub : Oppgave {
    override fun opprettOppgave(config: OppgaveConfig): Either<ClientError, Long> =
        Random.nextLong().right()
}
