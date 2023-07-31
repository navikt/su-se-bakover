package no.nav.su.se.bakover.domain.brev.command

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.Saksnummer

/**
 * Viktig informasjonsbrev for å innhente informasjon fra bruker før vi tar en beslutning (revurdering).
 */
data class ForhåndsvarselDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String,
) : GenererDokumentCommand
