package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr

/**
 * Viktig informasjonsbrev for å innhente informasjon fra bruker før vi tar en beslutning (revurdering).
 */
data class ForhåndsvarselDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String,
) : GenererDokumentCommand
