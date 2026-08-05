package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import java.util.UUID

interface OppgaveV2Client {
    fun opprettOppgave(
        config: OppgaveV2Data,
        representertEnhetsnr: String,
        idempotencyKey: UUID = UUID.randomUUID(),
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>

    fun opprettOppgaveMedSystembruker(
        config: OppgaveV2Data,
        idempotencyKey: UUID = UUID.randomUUID(),
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
}
