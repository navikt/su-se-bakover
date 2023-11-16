package tilbakekreving.domain.avbrutt

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import java.util.UUID

data class AvbruttTilbakekrevingsbehandlingDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val correlationId: CorrelationId?,
    override val sakId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String?,
) : GenererDokumentCommand, SakshendelseCommand {
    override val ident: NavIdentBruker? = null
    override val brukerroller: List<Brukerrolle> = emptyList()
}
