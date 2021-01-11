package no.nav.su.se.bakover.service.oppgave
import arrow.core.Either
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId

interface OppgaveService {
    fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId>
    fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId>
    fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeLukkeOppgave, Unit>
}
