package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.right
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import kotlin.random.Random

object OppgaveClientStub : OppgaveClient {

    override fun opprettOppgave(config: OppgaveConfig) = generateOppgaveId().right()

    override fun opprettOppgaveMedSystembruker(config: OppgaveConfig) = generateOppgaveId().right()

    override fun lukkOppgave(oppgaveId: OppgaveId) = Unit.right()

    override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId) = Unit.right()

    private fun generateOppgaveId() = OppgaveId(Random.nextLong(0, Long.MAX_VALUE).toString())
}
