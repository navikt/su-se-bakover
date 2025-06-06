package dokument.domain

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr

/**
 * Eies av domenet.
 * Brukes av domenelaget for å definere domenevariablene (typisk det vi har i databasen) i et brev.
 * Ting som Person og navn på ident holdes utenfor, da dette er ting som må hentes ekstern.
 */
interface GenererDokumentCommand {
    val fødselsnummer: Fnr
    val saksnummer: Saksnummer
    val sakstype: Sakstype
}
