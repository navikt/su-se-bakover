package no.nav.su.se.bakover.service.oppgave
import arrow.core.Either
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeFerdigstilleOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId

interface OppgaveService {
    fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId>
    fun ferdigstillFørstegangsoppgave(aktørId: AktørId): Either<KunneIkkeFerdigstilleOppgave, Unit>
    fun ferdigstillAttesteringsoppgave(aktørId: AktørId): Either<KunneIkkeFerdigstilleOppgave, Unit>
    fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeFerdigstilleOppgave, Unit>
}
