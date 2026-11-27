package tilbakekreving.domain.vedtaksbrev

import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
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
    override val saksbehandler: NavIdentBruker,
    override val attestant: NavIdentBruker?,
    val fritekst: String?,
    val vurderingerMedKrav: VurderingerMedKrav,
    val skalTilbakekreve: Boolean,
    val dødsbo: Boolean,
) : BrevmalCommand,
    SakshendelseCommand {
    override val mal: Brevmal<VedtaksbrevTilbakekrevingsbehandlingDokumentCommand> get() = vedtaksbrevTilbakekrevingBrev
    override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
    override val utførtAv: NavIdentBruker = saksbehandler
    override val brukerroller: List<Brukerrolle> = emptyList()
}
