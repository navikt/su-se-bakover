package no.nav.su.se.bakover.domain.oppgave

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype

/**
 * Velg ut de verdiene du har lyst til å oppdatere
 */
data class OppdaterOppgaveInfo(
    val beskrivelse: String,
    val oppgavetype: Oppgavetype? = null,
    val status: String? = null,
    val tilordnetRessurs: TilordnetRessurs,
) {
    sealed interface TilordnetRessurs {
        /** Endrer til denne navIdenten*/
        data class NavIdent(val navIdent: String) : TilordnetRessurs

        /** Fjerner knytningen til navIdent, denne vil legges åpent på benken */
        data object IkkeTilordneRessurs : TilordnetRessurs

        /** Beholder nåværende navIdent som tilordnet ressurs */
        data object Uendret : TilordnetRessurs
    }
}

interface OppgaveClient {
    fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
    fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveHttpKallResponse>
    fun lukkOppgave(
        oppgaveId: OppgaveId,
        tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
    ): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse>

    fun lukkOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
        tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
    ): Either<KunneIkkeLukkeOppgave, OppgaveHttpKallResponse>

    fun oppdaterOppgave(
        oppgaveId: OppgaveId,
        oppdatertOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse>

    fun oppdaterOppgaveMedSystembruker(
        oppgaveId: OppgaveId,
        oppdatertOppgaveInfo: OppdaterOppgaveInfo,
    ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse>

    fun hentOppgave(oppgaveId: OppgaveId): Either<KunneIkkeSøkeEtterOppgave, Oppgave>
    fun hentOppgaveMedSystembruker(oppgaveId: OppgaveId): Either<KunneIkkeSøkeEtterOppgave, Oppgave>
}
