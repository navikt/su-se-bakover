package no.nav.su.se.bakover.kontrollsamtale.domain.endre

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler

fun Sak.endreKontrollsamtale(
    command: EndreKontrollsamtaleCommand,
    kontrollsamtaler: Kontrollsamtaler,
): Either<KunneIkkeEndreKontrollsamtale, Pair<Kontrollsamtale, Kontrollsamtaler>> {
    return (kontrollsamtaler.hentKontrollsamtale(command.kontrollsamtaleId)!! to kontrollsamtaler).right()
}
