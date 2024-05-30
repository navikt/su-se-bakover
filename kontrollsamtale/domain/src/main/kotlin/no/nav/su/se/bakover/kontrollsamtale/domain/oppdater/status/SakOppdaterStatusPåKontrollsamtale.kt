package no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler

fun Sak.oppdaterStatusPåKontrollsamtale(
    command: OppdaterStatusPåKontrollsamtaleCommand,
    kontrollsamtaler: Kontrollsamtaler,
): Either<KunneIkkeOppdatereStatusPåKontrollsamtale, Pair<Kontrollsamtale, Kontrollsamtaler>> {
    return kontrollsamtaler.oppdaterStatus(command, kontrollsamtaler)
}
