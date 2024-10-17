package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import java.util.UUID

data class AnnullerKravgrunnlagCommand(
    override val sakId: UUID,
    override val correlationId: CorrelationId?,
    override val brukerroller: List<Brukerrolle>,
    val annullertAv: NavIdentBruker.Saksbehandler,
    val kravgrunnlagHendelseId: HendelseId,
    val klientensSisteSaksversjon: Hendelsesversjon,
) : SakshendelseCommand {
    override val utførtAv: NavIdentBruker = annullertAv

    fun toDefaultHendelsesMetadata() = DefaultHendelseMetadata(
        correlationId = correlationId,
        ident = this.utførtAv,
        brukerroller = brukerroller,
    )
}
