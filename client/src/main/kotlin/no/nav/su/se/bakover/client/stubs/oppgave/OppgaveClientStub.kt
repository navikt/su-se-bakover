package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.right
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import kotlin.random.Random

object OppgaveClientStub : OppgaveClient {

    override fun opprettOppgave(config: OppgaveConfig) = generateOppgaveId().right()

    override fun ferdigstillFørstegangsoppgave(aktørId: AktørId) = Unit.right()

    override fun ferdigstillAttesteringsoppgave(aktørId: AktørId) = Unit.right()

    override fun lukkOppgave(oppgaveId: OppgaveId) = Unit.right()

    private fun generateOppgaveId() = OppgaveId(Random.nextLong().toString())
}
