package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.time.LocalDate
import java.util.UUID

interface BehandlingService {
    fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling>
    fun underkjenn(
        behandlingId: UUID,
        attestant: Attestant,
        underkjennelse: Attestering.Underkjennelse
    ): Either<KunneIkkeUnderkjenneBehandling, Behandling>

    fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        saksbehandler: Saksbehandler,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Either<KunneIkkeOppdatereBehandlingsinformasjon, Behandling>
    fun opprettBeregning(
        behandlingId: UUID,
        saksbehandler: Saksbehandler,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag>
    ): Either<KunneIkkeBeregne, Behandling>

    fun simuler(behandlingId: UUID, saksbehandler: Saksbehandler): Either<KunneIkkeSimulereBehandling, Behandling>
    fun sendTilAttestering(
        behandlingId: UUID,
        saksbehandler: Saksbehandler
    ): Either<KunneIkkeSendeTilAttestering, Behandling>

    fun iverksett(
        behandlingId: UUID,
        attestant: Attestant
    ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling>

    fun opprettSøknadsbehandling(søknadId: UUID): Either<KunneIkkeOppretteSøknadsbehandling, Behandling>
    fun lagBrevutkast(behandlingId: UUID): Either<KunneIkkeLageBrevutkast, ByteArray>
}

sealed class KunneIkkeLageBrevutkast {
    data class KanIkkeLageBrevutkastForStatus(val status: BehandlingsStatus) : KunneIkkeLageBrevutkast()
    object FantIkkeBehandling : KunneIkkeLageBrevutkast()
    object KunneIkkeLageBrev : KunneIkkeLageBrevutkast()
    object FantIkkePerson : KunneIkkeLageBrevutkast()
    object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkast()
}

object FantIkkeBehandling
sealed class KunneIkkeOppretteSøknadsbehandling {
    object FantIkkeSøknad : KunneIkkeOppretteSøknadsbehandling()
    object SøknadManglerOppgave : KunneIkkeOppretteSøknadsbehandling()
    object SøknadErLukket : KunneIkkeOppretteSøknadsbehandling()
    object SøknadHarAlleredeBehandling : KunneIkkeOppretteSøknadsbehandling()
}

sealed class KunneIkkeBeregne {
    object FantIkkeBehandling : KunneIkkeBeregne()
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeBeregne()
}

sealed class KunneIkkeSimulereBehandling {
    object KunneIkkeSimulere : KunneIkkeSimulereBehandling()
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeSimulereBehandling()
    object FantIkkeBehandling : KunneIkkeSimulereBehandling()
}

sealed class KunneIkkeSendeTilAttestering {
    object FantIkkeBehandling : KunneIkkeSendeTilAttestering()
    object KunneIkkeFinneAktørId : KunneIkkeSendeTilAttestering()
    object KunneIkkeOppretteOppgave : KunneIkkeSendeTilAttestering()
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeSendeTilAttestering()
}

sealed class KunneIkkeUnderkjenneBehandling {
    object FantIkkeBehandling : KunneIkkeUnderkjenneBehandling()
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeUnderkjenneBehandling()
    object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenneBehandling()
    object FantIkkeAktørId : KunneIkkeUnderkjenneBehandling()
}

sealed class KunneIkkeIverksetteBehandling {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteBehandling()
    object KunneIkkeUtbetale : KunneIkkeIverksetteBehandling()
    object KunneIkkeKontrollsimulere : KunneIkkeIverksetteBehandling()
    object SimuleringHarBlittEndretSidenSaksbehandlerSimulerte : KunneIkkeIverksetteBehandling()
    object KunneIkkeJournalføreBrev : KunneIkkeIverksetteBehandling()
    object FantIkkeBehandling : KunneIkkeIverksetteBehandling()
    object FantIkkePerson : KunneIkkeIverksetteBehandling()
    object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeIverksetteBehandling()
}

sealed class KunneIkkeOppdatereBehandlingsinformasjon {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeOppdatereBehandlingsinformasjon()
    object FantIkkeBehandling : KunneIkkeOppdatereBehandlingsinformasjon()
}
sealed class IverksattBehandling {
    abstract val behandling: Behandling

    data class UtenMangler(override val behandling: Behandling) : IverksattBehandling()

    sealed class MedMangler : IverksattBehandling() {
        data class KunneIkkeJournalføreBrev(override val behandling: Behandling) : MedMangler()
        data class KunneIkkeDistribuereBrev(override val behandling: Behandling) : MedMangler()
        data class KunneIkkeLukkeOppgave(override val behandling: Behandling) : MedMangler()
    }
}
