package no.nav.su.se.bakover.domain.brev.command

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.Saksnummer

/**
 * Eies av domenet.
 * Brukes av domenelaget for å definere domenevariablene (typisk det vi har i databasen) i et brev.
 * Ting som Person og navn på ident holdes utenfor, da dette er ting som må hentes ekstern.
 */
sealed interface GenererDokumentCommand {
    val fødselsnummer: Fnr
    val saksnummer: Saksnummer
}
