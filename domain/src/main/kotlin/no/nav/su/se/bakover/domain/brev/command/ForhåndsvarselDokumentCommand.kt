package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.jsonRequest.forhåndsvarselBrev

/**
 * Viktig informasjonsbrev for å innhente informasjon fra bruker før vi tar en beslutning (revurdering).
 */
data class ForhåndsvarselDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String,
) : BrevmalCommand {
    override val attestant: NavIdentBruker? get() = null
    override val mal: Brevmal<ForhåndsvarselDokumentCommand> get() = forhåndsvarselBrev
    override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
}
