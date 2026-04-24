package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import java.util.UUID

interface OppgaveV2Client {
    fun opprettOppgave(
        config: OppgaveV2Config,
        representertEnhetsnr: String,
        idempotencyKey: UUID = UUID.randomUUID(),
        include: List<String> = emptyList(),
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>

    fun opprettOppgaveMedSystembruker(
        config: OppgaveV2Config,
        idempotencyKey: UUID = UUID.randomUUID(),
        include: List<String> = emptyList(),
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
}

object OppgaveV2Includes {
    const val KOMMENTARER = "kommentarer"
}
