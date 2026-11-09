package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import java.time.Clock
import java.util.UUID

interface OppgaveV2Service {
    fun opprettOppgave(
        config: OppgaveV2Data,
        representertEnhetsnr: String,
        idempotencyKey: UUID,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>

    fun opprettOppgaveMedSystembruker(
        saksnummer: Saksnummer,
        fnr: Fnr,
        sakstype: Sakstype,
        personhendelser: Collection<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>,
        clock: Clock,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
}
