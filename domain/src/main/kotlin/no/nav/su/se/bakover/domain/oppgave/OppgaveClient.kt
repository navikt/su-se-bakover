package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.domain.AktørId

interface OppgaveClient {
    fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, Long>
    fun ferdigstillFørstegangsOppgave(aktørId: AktørId): Either<KunneIkkeFerdigstilleOppgave, Int>
    fun ferdigstillAttesteringsOppgave(aktørId: AktørId): Either<KunneIkkeFerdigstilleOppgave, Int>
}

object KunneIkkeOppretteOppgave
object KunneIkkeFerdigstilleOppgave
