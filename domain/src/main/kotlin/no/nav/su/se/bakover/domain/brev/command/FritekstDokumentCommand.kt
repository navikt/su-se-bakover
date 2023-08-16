package no.nav.su.se.bakover.domain.brev.command

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.Saksnummer

/**
 * Et generelt fritekstbrev som knyttes til selve saken og ikke en spesifikk behandling (innholdet er saksbehandlergenerert og kan være knyttet til hva som helst).
 */
data class FritekstDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val brevTittel: String,
    val fritekst: String,
) : GenererDokumentCommand
