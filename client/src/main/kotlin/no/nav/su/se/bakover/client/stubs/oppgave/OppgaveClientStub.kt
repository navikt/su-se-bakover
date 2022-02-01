package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import kotlin.random.Random

object OppgaveClientStub : OppgaveClient {

    override fun opprettOppgave(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> =
        generateOppgaveId().right().also { log.info("OppgaveClientStub oppretter oppgave: $config") }

    override fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> =
        generateOppgaveId().right().also { log.info("OppgaveClientStub oppretter oppgave med systembruker: $config") }

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> =
        Unit.right().also { log.info("OppgaveClientStub lukker oppgave med oppgaveId: $oppgaveId") }

    override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> =
        Unit.right().also { log.info("OppgaveClientStub lukker oppgave med systembruker og oppgaveId: $oppgaveId") }

    override fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        beskrivelse: String,
    ): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit> =
        Unit.right().also { log.info("OppgaveClientStub oppdaterer oppgave $oppgaveId med beskrivelse: $beskrivelse") }

    private fun generateOppgaveId() = OppgaveId(Random.nextLong(0, Long.MAX_VALUE).toString())
}
