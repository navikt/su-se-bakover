package no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status

import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import java.util.UUID

sealed interface KunneIkkeOppdatereStatusPåKontrollsamtale {
    data class UgyldigStatusovergang(
        val kontrollsamtaleId: UUID,
        val gyldigeOverganger: Set<Kontrollsamtalestatus>,
    ) : KunneIkkeOppdatereStatusPåKontrollsamtale
}
