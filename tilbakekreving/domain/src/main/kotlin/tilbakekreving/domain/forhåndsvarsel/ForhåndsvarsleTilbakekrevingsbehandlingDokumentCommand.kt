package tilbakekreving.domain.forhåndsvarsel

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr

data class ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String?,
) : GenererDokumentCommand
