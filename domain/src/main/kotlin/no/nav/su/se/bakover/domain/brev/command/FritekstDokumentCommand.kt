package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.Distribusjonstype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.jsonRequest.fritekstAnnetBrev
import no.nav.su.se.bakover.domain.brev.jsonRequest.fritekstVedtakBrev
import no.nav.su.se.bakover.domain.brev.jsonRequest.fritekstViktigBrev

/**
 * Et generelt fritekstbrev som knyttes til selve saken og ikke en spesifikk behandling (innholdet er saksbehandlergenerert og kan være knyttet til hva som helst).
 */
data class FritekstDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    val brevTittel: String,
    val fritekst: String,
    val distribusjonstype: Distribusjonstype,
) : BrevmalCommand {
    override val attestant: NavIdentBruker? get() = null
    override val mal: Brevmal<FritekstDokumentCommand>
        get() = when (distribusjonstype) {
            Distribusjonstype.VEDTAK -> fritekstVedtakBrev
            Distribusjonstype.VIKTIG -> fritekstViktigBrev
            Distribusjonstype.ANNET -> fritekstAnnetBrev
        }
    override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
}
