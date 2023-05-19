package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object OppgaveClientStub : OppgaveClient {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun opprettOppgave(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> =
        OppgaveId("stubbedOppgaveId").right().also { log.info("OppgaveClientStub oppretter oppgave: $config") }

    override fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> =
        OppgaveId("stubbedOppgaveId").right().also { log.info("OppgaveClientStub oppretter oppgave med systembruker: $config") }

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> =
        Unit.right().also { log.info("OppgaveClientStub lukker oppgave med oppgaveId: $oppgaveId") }

    override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<OppgaveFeil.KunneIkkeLukkeOppgave, Unit> =
        Unit.right().also { log.info("OppgaveClientStub lukker oppgave med systembruker og oppgaveId: $oppgaveId") }

    override fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        beskrivelse: String,
    ): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit> =
        Unit.right().also { log.info("OppgaveClientStub oppdaterer oppgave $oppgaveId med beskrivelse: $beskrivelse") }
}
