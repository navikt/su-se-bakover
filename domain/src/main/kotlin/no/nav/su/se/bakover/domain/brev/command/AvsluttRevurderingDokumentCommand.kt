package no.nav.su.se.bakover.domain.brev.command

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.Saksnummer

/**
 * Dersom man avslutter en forhåndsvarslet revurdering, så sender vi et brev som informerer om at de kan se bort fra forhåndsvarselet.
 * Dette er et informasjonsbrev og ikke et enkeltvedtak.
 */
data class AvsluttRevurderingDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String?,
) : GenererDokumentCommand
