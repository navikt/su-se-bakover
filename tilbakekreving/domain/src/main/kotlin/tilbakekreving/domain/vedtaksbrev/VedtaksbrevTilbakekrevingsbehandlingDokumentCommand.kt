package tilbakekreving.domain.vedtaksbrev

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.util.UUID

/**
 * @property attestant er null når saksbehandler skal forhåndsvise brevet.
 */
data class VedtaksbrevTilbakekrevingsbehandlingDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val correlationId: CorrelationId?,
    override val sakId: UUID,
    override val sakstype: Sakstype,
    val saksbehandler: NavIdentBruker,
    val attestant: NavIdentBruker?,
    val fritekst: String?,
    val vurderingerMedKrav: VurderingerMedKrav,
    val skalTilbakekreve: Boolean,
) : GenererDokumentCommand,
    SakshendelseCommand {
    override val utførtAv: NavIdentBruker = saksbehandler
    override val brukerroller: List<Brukerrolle> = emptyList()
}
