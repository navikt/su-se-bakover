package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving

/**
 * Viktig informasjonsbrev for å innhente informasjon fra bruker før vi tar en beslutning (revurdering med tilbakekreving).
 */
data class ForhåndsvarselTilbakekrevingDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String,
    val bruttoTilbakekreving: Int,
    val tilbakekreving: Tilbakekreving,
) : GenererDokumentCommand
