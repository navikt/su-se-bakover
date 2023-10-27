package tilbakekreving.domain.avbrutt

import arrow.core.Nel
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class AvbrytTilbakekrevingsbehandlingCommand(
    override val sakId: UUID,
    val behandlingsId: TilbakekrevingsbehandlingId,
    val utførtAv: NavIdentBruker.Saksbehandler,
    override val correlationId: CorrelationId,
    override val brukerroller: Nel<Brukerrolle>,
    val klientensSisteSaksversjon: Hendelsesversjon,
    val brevvalg: Brevvalg.SaksbehandlersValg,
    val begrunnelse: String,
) : SakshendelseCommand {
    override val ident: NavIdentBruker = utførtAv

    fun defaultHendelseMetadata() = DefaultHendelseMetadata(
        correlationId = correlationId,
        ident = utførtAv,
        brukerroller = brukerroller,
    )
}
