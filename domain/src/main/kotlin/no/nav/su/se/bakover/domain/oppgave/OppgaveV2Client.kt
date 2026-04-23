package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import java.util.UUID

interface OppgaveV2Client {
    fun opprettOppgave(
        config: OppgaveConfig,
        idempotencyKey: UUID = UUID.randomUUID(),
        include: Set<OppgaveV2Include> = emptySet(),
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>

    fun opprettOppgaveMedSystembruker(
        config: OppgaveConfig,
        idempotencyKey: UUID = UUID.randomUUID(),
        include: Set<OppgaveV2Include> = emptySet(),
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
}

enum class OppgaveV2Include(val value: String) {
    KOMMENTARER("kommentarer"),
}
