package no.nav.su.se.bakover.client.stubs.oppgave

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgave
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data object OppgaveClientStub : OppgaveClient {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun opprettOppgave(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> =
        OppgaveId("stubbedOppgaveId").right().also { log.info("OppgaveClientStub oppretter oppgave: $config") }

    override fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<OppgaveFeil.KunneIkkeOppretteOppgave, OppgaveId> =
        OppgaveId("stubbedOppgaveId").right()
            .also { log.info("OppgaveClientStub oppretter oppgave med systembruker: $config") }

    override fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, Unit> =
        Unit.right().also { log.info("OppgaveClientStub lukker oppgave med oppgaveId: $oppgaveId") }

    override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, Unit> =
        Unit.right().also { log.info("OppgaveClientStub lukker oppgave med systembruker og oppgaveId: $oppgaveId") }

    override fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        beskrivelse: String,
    ): Either<KunneIkkeOppdatereOppgave, Unit> =
        Unit.right().also { log.info("OppgaveClientStub oppdaterer oppgave $oppgaveId med beskrivelse: $beskrivelse") }

    override fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        oppdatertOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, Unit> =
        Unit.right().also { log.info("OppgaveClientStub oppdaterer oppgave $oppgaveId med data: $oppdatertOppgaveInfo") }

    override fun hentOppgave(
        oppgaveId: OppgaveId,
    ): Either<OppgaveFeil.KunneIkkeSøkeEtterOppgave, Oppgave> {
        return Oppgave(
            id = oppgaveId,
            versjon = 1,
            status = Oppgave.Oppgavestatus.Opprettet,
        ).also { log.info("OppgaveClientStub hentet oppgave $it") }.right()
    }

    override fun hentOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
    ): Either<OppgaveFeil.KunneIkkeSøkeEtterOppgave, Oppgave> {
        return Oppgave(
            id = oppgaveId,
            versjon = 1,
            status = Oppgave.Oppgavestatus.Opprettet,
        ).also { log.info("OppgaveClientStub hentet oppgave $it") }.right()
    }
}
