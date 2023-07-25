package no.nav.su.se.bakover.web.routes.søknadsbehandling.attester

import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering

fun KunneIkkeSendeSøknadsbehandlingTilAttestering.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeSendeSøknadsbehandlingTilAttestering.KunneIkkeOppretteOppgave -> {
            Feilresponser.kunneIkkeOppretteOppgave
        }

        is KunneIkkeSendeSøknadsbehandlingTilAttestering.KunneIkkeFinneAktørId -> {
            Feilresponser.fantIkkeAktørId
        }

        KunneIkkeSendeSøknadsbehandlingTilAttestering.InneholderUfullstendigBosituasjon -> Feilresponser.inneholderUfullstendigeBosituasjoner
        is KunneIkkeSendeSøknadsbehandlingTilAttestering.UgyldigTilstand -> ugyldigTilstand(
            this.fra,
            this.til,
        )
    }
}
