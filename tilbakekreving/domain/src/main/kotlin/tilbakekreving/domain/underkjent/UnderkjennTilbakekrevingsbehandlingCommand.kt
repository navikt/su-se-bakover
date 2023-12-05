package tilbakekreving.domain.underkjent

import arrow.core.Nel
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class UnderkjennTilbakekrevingsbehandlingCommand(
    override val sakId: UUID,
    val behandlingsId: TilbakekrevingsbehandlingId,
    override val utførtAv: NavIdentBruker.Attestant,
    override val correlationId: CorrelationId,
    override val brukerroller: Nel<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
    val grunn: UnderkjennAttesteringsgrunnTilbakekreving,
    val kommentar: String,
) : SakshendelseCommand {
    fun toDefaultHendelsesMetadata() = DefaultHendelseMetadata(
        correlationId = correlationId,
        ident = this.utførtAv,
        brukerroller = brukerroller,
    )
}
