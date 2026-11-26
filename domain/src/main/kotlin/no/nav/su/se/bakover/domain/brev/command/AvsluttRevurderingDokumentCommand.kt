package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.jsonRequest.avsluttRevurderingBrev

/**
 * Dersom man avslutter en forhåndsvarslet revurdering, så sender vi et brev som informerer om at de kan se bort fra forhåndsvarselet.
 * Dette er et informasjonsbrev og ikke et enkeltvedtak.
 */
data class AvsluttRevurderingDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String?,
) : BrevmalCommand {
    override val attestant: NavIdentBruker? get() = null
    override val mal: Brevmal<AvsluttRevurderingDokumentCommand> get() = avsluttRevurderingBrev
    override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
}
