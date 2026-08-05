package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import java.time.Clock

interface OppgaveV2Service {
    fun opprettOppgaveMedSystembruker(
        saksnummer: Saksnummer,
        fnr: Fnr,
        sakstype: Sakstype,
        personhendelser: Collection<Personhendelse.TilknyttetSak.IkkeSendtTilOppgave>,
        clock: Clock,
    ): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
}
