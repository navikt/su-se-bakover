package tilbakekreving.domain.forhåndsvarsel

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.vurdert.VurderingerMedKrav
import java.util.UUID

data class VedtaksbrevTilbakekrevingsbehandlingDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val correlationId: CorrelationId?,
    override val sakId: UUID,
    /**
     * TODO - her burde vi bare tillate saksbehandler + attestant
     */
    val saksbehandler: NavIdentBruker,
    val fritekst: String?,
    val vurderingerMedKrav: VurderingerMedKrav,
) : GenererDokumentCommand, SakshendelseCommand {
    override val utførtAv: NavIdentBruker = saksbehandler
    override val brukerroller: List<Brukerrolle> = emptyList()
}
